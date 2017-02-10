/*
 * Copyright (c) 2015-2016 Sebastien Serre <ssbx@sysmo.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sysmo.nchecks.impl;

import com.ericsson.otp.erlang.OtpErlangDecodeException;
import com.ericsson.otp.erlang.OtpErlangExit;
import io.sysmo.nchecks.Argument;
import io.sysmo.nchecks.CheckInterface;
import io.sysmo.nchecks.HelperInterface;
import io.sysmo.nchecks.HelperReply;
import io.sysmo.nchecks.NChecksJRuby;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.StateClient;
import io.sysmo.nchecks.checks.*;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangChar;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangString;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.ericsson.otp.erlang.OtpMbox;

import io.sysmo.nchecks.snmp.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;

public class ErlangNodeNChecks implements Runnable {

    public static final OtpErlangAtom ATOM_REPLY = new OtpErlangAtom("reply");
    public static final OtpErlangAtom ATOM_OK = new OtpErlangAtom("ok");
    public static final OtpErlangAtom ATOM_ERROR = new OtpErlangAtom("error");
    public static final OtpErlangAtom ATOM_QUEUE_FULL
            = new OtpErlangAtom("queue_full");

    // otp
    private final String nodeName;
    private final OtpMbox mbox;
    private static final Object LOCK = new Object();

    // nchecks vars
    private final ThreadPoolExecutor threadPool;

    static ErlangNodeNChecks instance = null;
    static Logger logger = LoggerFactory.getLogger(ErlangNodeNChecks.class);

    /**
     * Start a Nchecks application that communicate with an erlang server.
     *
     * @param mbox The jserver_nchecks mailbox
     * @param nodeName The name of the foreign node
     * @param rubyDir The ruby script dir
     * @param utilsDir The utils dir
     * @param etcDir The config dir
     * @param stateServer the state server address
     * @param stateServerPort the state server port
     * @return An ErlangNodeNChecks Instance
     * @throws Exception startup error
     */
    public static synchronized ErlangNodeNChecks getInstance(
            final OtpMbox mbox, final String nodeName,
            final String rubyDir, final String utilsDir,
            final String etcDir, final InetAddress stateServer,
            final int stateServerPort) throws Exception {
        if (ErlangNodeNChecks.instance == null) {
            ErlangNodeNChecks.instance = new ErlangNodeNChecks(mbox, nodeName, rubyDir,
                    utilsDir, etcDir, stateServer, stateServerPort);
        }
        return ErlangNodeNChecks.instance;
    }

    private ErlangNodeNChecks(
            final OtpMbox mbox, final String nodeName,
            final String rubyDir, final String utilsDir,
            final String etcDir, final InetAddress stateServer,
            final int stateServerPort) throws Exception {
        this.nodeName = nodeName;
        this.mbox = mbox;

        ErlangNodeNChecks.logger.info("ruby dir is: " + rubyDir);

        // init thread pool
        this.threadPool = new ThreadPoolExecutor(
                8, // base pool size
                20, // max pool size
                60, // return to base after
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<Runnable>(2000), // queue capacity
                new NChecksPoolReject());

        // initialize special CheckICMP class
        CheckICMP.setPping(utilsDir);
        ErlangNodeNChecks.logger.info("CheckICMP init with path: " + utilsDir);

        // initialize .rb script cache
        NChecksJRuby.startJRubyCache(rubyDir, etcDir);
        ErlangNodeNChecks.logger.info("JRuby init with path: " + rubyDir);

        // initialize snmpman
        Manager.start(etcDir);
        ErlangNodeNChecks.logger.info("SNMP started");

        // initialize state client
        StateClient.start(stateServer, stateServerPort);
    }

    @Override
    public void run() {
        // loop and wait for calls
        ErlangNodeNChecks.logger.info("begin too loop");
        OtpErlangObject call;
        while (true) {
            try {
                call = this.mbox.receive();
                this.handleMsg(call);
            } catch (OtpErlangExit e) {
                ErlangNodeNChecks.logger.info(e.getMessage(), e);
                break;
            } catch (OtpErlangDecodeException e) {
                ErlangNodeNChecks.logger.error(e.getMessage(), e);
                break;
            }
        }
        this.threadPool.shutdownNow();
        StateClient.stop();
        Manager.stop();
        this.mbox.exit("crach");
    }

    /**
     * Send a message to the caller. Used by SnmpmanResponseListener
     * SnmpmanTreeListener and SnmpmanTableListener which are executed in the
     * asynchroneously in another thread.
     *
     * @param to destination
     * @param msg message
     */
    public static void sendReply(
            final OtpErlangObject to, final OtpErlangObject msg) {
        OtpErlangObject[] obj = new OtpErlangObject[3];
        obj[0] = ATOM_REPLY;
        obj[1] = to;
        obj[2] = msg;
        OtpErlangTuple tuple = new OtpErlangTuple(obj);
        synchronized (ErlangNodeNChecks.LOCK) {
            ErlangNodeNChecks.instance.mbox.send(
                    "j_server_nchecks", ErlangNodeNChecks.instance.nodeName, tuple);
        }
    }

    public static OtpErlangTuple buildOkReply(final OtpErlangObject msg) {
        OtpErlangObject[] valObj = new OtpErlangObject[2];
        valObj[0] = ATOM_OK;
        valObj[1] = msg;
        return new OtpErlangTuple(valObj);
    }

    public static OtpErlangTuple buildErrorReply(final OtpErlangObject msg) {
        OtpErlangObject[] valObj = new OtpErlangObject[2];
        valObj[0] = ATOM_ERROR;
        valObj[1] = msg;
        return new OtpErlangTuple(valObj);
    }

    public static OtpErlangTuple buildQueueFullReply(final OtpErlangObject msg) {
        OtpErlangObject[] valObj = new OtpErlangObject[2];
        valObj[0] = ATOM_QUEUE_FULL;
        valObj[1] = msg;
        return new OtpErlangTuple(valObj);
    }

    private void handleInit(OtpErlangTuple initMsg) {
        ErlangNodeNChecks.logger.info("init?" + initMsg.toString());
    }

    private void handleMsg(final OtpErlangObject msg) {
        OtpErlangTuple tuple;
        OtpErlangAtom command;
        OtpErlangObject caller;
        OtpErlangTuple payload;
        try {
            tuple = (OtpErlangTuple) msg;
            command = (OtpErlangAtom) (tuple.elementAt(0));
            caller = tuple.elementAt(1);
            payload = (OtpErlangTuple) (tuple.elementAt(2));
        } catch (Exception | Error e) {
            ErlangNodeNChecks.logger.warn(
                    "Fail to decode tuple: " + e.getMessage(), e);
            return;
        }

        try {
            String cmdString = command.toString();

            switch (cmdString) {
                case "check":
                    OtpErlangString erlangCheckClassName = (OtpErlangString) (payload.elementAt(0));

                    String checkClassName = erlangCheckClassName.stringValue();

                    OtpErlangList checkArgs = (OtpErlangList) (payload.elementAt(1));

                    OtpErlangString checkId = (OtpErlangString) (payload.elementAt(2));

                    Runnable checkWorker = new NChecksRunnable(
                            Class.forName(checkClassName).newInstance(),
                            caller,
                            checkArgs,
                            checkId);
                    this.threadPool.execute(checkWorker);
                    break;

                case "helper":
                    OtpErlangString erlangHelperClassName
                            = (OtpErlangString) (payload.elementAt(0));
                    String helperClassName = erlangHelperClassName.stringValue();
                    OtpErlangList args
                            = (OtpErlangList) (payload.elementAt(1));
                    Runnable helperWorker = new NHelperRunnable(
                            Class.forName(helperClassName).newInstance(),
                            caller,
                            args);
                    this.threadPool.execute(helperWorker);
                    break;

                case "init":
                    this.handleInit(payload);
                    break;

                case "cleanup":
                    Manager.cleanup();
                    break;

                default:
                    OtpErlangObject reply = buildErrorReply(command);
                    ErlangNodeNChecks.sendReply(caller, reply);
            }
        } catch (Exception | Error e) {
            ErlangNodeNChecks.logger.error(e.getMessage(), e);
            OtpErlangTuple reply = buildErrorReply(
                    new OtpErlangString("ErlangNodeNChecks error: " + e
                            + " " + command.toString() + " -> " + e.getMessage())
            );
            ErlangNodeNChecks.sendReply(caller, reply);
        }
    }

    public static Map<String, Argument> decodeArgs(final OtpErlangList argList) {
        Map<String, Argument> result = new HashMap<>();
        Iterator<OtpErlangObject> itr = argList.iterator();
        OtpErlangTuple element;
        while (itr.hasNext()) {
            element = (OtpErlangTuple) (itr.next());
            OtpErlangString key = (OtpErlangString) (element.elementAt(0));
            OtpErlangObject val = element.elementAt(1);
            if (val.getClass() == OtpErlangString.class) {
                OtpErlangString valStr = (OtpErlangString) (element.elementAt(1));
                Argument a = new Argument();
                a.set(valStr.stringValue());
                result.put(key.stringValue(), a);
            } else {
                // Currently only string arguments are accepted
                ErlangNodeNChecks.logger.info("Unknown arg type: " + val);
            }
        }
        return result;
    }

    interface CheckCaller {

        OtpErlangObject getCaller();
    }

    static class NChecksPoolReject implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            OtpErlangString errMsg
                    = new OtpErlangString("ErlangNodeNChecks thread queue is full");
            CheckCaller ncheckRun = (CheckCaller) r;
            OtpErlangObject caller = ncheckRun.getCaller();
            OtpErlangObject reply = ErlangNodeNChecks.buildQueueFullReply(errMsg);
            ErlangNodeNChecks.sendReply(caller, reply);
        }
    }

    static class NChecksRunnable implements Runnable, CheckCaller {

        private final CheckInterface check;
        private final OtpErlangObject caller;
        private final OtpErlangList args;
        private final String stateId;

        public NChecksRunnable(
                Object checkObj,
                OtpErlangObject callerObj,
                OtpErlangList argsList,
                OtpErlangString stateId) {
            this.check = (CheckInterface) checkObj;
            this.caller = callerObj;
            this.args = argsList;
            this.stateId = stateId.stringValue();
        }

        @Override
        public void run() {
            // build a query from arguments
            Query query
                    = new Query(ErlangNodeNChecks.decodeArgs(this.args), this.stateId);

            // execute and get reply
            Reply reply = this.check.execute(query);

            // maybe push state to state server
            reply.pushState(this.stateId);

            // build and send erlang reply
            OtpErlangObject replyMsg = ErlangNodeNChecks.buildOkReply(reply.asTuple());
            ErlangNodeNChecks.sendReply(this.caller, replyMsg);
        }

        @Override
        public OtpErlangObject getCaller() {
            return this.caller;
        }
    }

    static class NHelperRunnable implements Runnable, CheckCaller {

        private final HelperInterface helper;
        private final OtpErlangObject caller;
        private final OtpErlangList args;

        public NHelperRunnable(
                final Object helpObj,
                final OtpErlangObject callerObj,
                final OtpErlangList argsList) {
            this.helper = (HelperInterface) helpObj;
            this.caller = callerObj;
            this.args = argsList;
        }

        @Override
        public void run() {
            Query query = new Query(ErlangNodeNChecks.decodeArgs(this.args));
            HelperReply helperReply = helper.callHelper(query);
            OtpErlangList jsonCharList
                    = this.buildErlangCharList(helperReply.toCharArray());
            OtpErlangObject replyMsg = ErlangNodeNChecks.buildOkReply(jsonCharList);
            ErlangNodeNChecks.sendReply(this.caller, replyMsg);
        }

        @Override
        public OtpErlangObject getCaller() {
            return this.caller;
        }

        private OtpErlangList buildErlangCharList(char[] charList) {
            OtpErlangObject[] objList = new OtpErlangObject[charList.length];
            for (int i = 0; i < charList.length; i++) {
                objList[i] = new OtpErlangChar(charList[i]);
            }
            return new OtpErlangList(objList);
        }
    }
}
