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
package io.sysmo.nchecks.checks;

import io.sysmo.nchecks.CheckInterface;
import io.sysmo.nchecks.Argument;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Query;

import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.InetAddress;

import io.sysmo.nchecks.Status;
import java.io.IOException;
import java.net.UnknownHostException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class CheckTCP implements CheckInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckTCP.class);

    private String host = "";
    private int port = 0;
    private int msWarning = 500;
    private int msCritical = 2500;
    private int msTimeout = 5000;
    private Status refuseState = Status.CRITICAL;
    private Status acceptState = Status.OK;

    public CheckTCP() {
    }

    @Override
    public Reply execute(Query query) {
        Reply reply = new Reply();

        Argument hostArg = query.get("host");
        Argument portArg = query.get("port");
        Argument msWarningArg = query.get("ms_warning");
        Argument msCriticalArg = query.get("ms_critical");
        Argument msTimeoutArg = query.get("ms_timeout");
        Argument refuseStateArg = query.get("refuse");
        Argument acceptStateArg = query.get("accept");
        /* TODO
        Argument useIpv6        = query.get("force_ipv6");
        Argument escapeChars    = query.get("escape_chars");
        Argument send_string    = query.get("send_string");
        Argument expect_string  = query.get("expect_string");
         */

        try {
            if (hostArg != null) {
                host = hostArg.asString();
            }
            if (portArg != null) {
                port = portArg.asInteger();
            }
            if (msWarningArg != null) {
                msWarning = msWarningArg.asInteger();
            }
            if (msCriticalArg != null) {
                msCritical = msCriticalArg.asInteger();
            }
            if (msTimeoutArg != null) {
                msTimeout = msTimeoutArg.asInteger();
            }
            if (refuseStateArg != null) {
                refuseState = Status.fromString(refuseStateArg.asString());
            }
            if (acceptStateArg != null) {
                acceptState = Status.fromString(acceptStateArg.asString());
            }
        } catch (Exception | Error e) {
            CheckTCP.LOGGER.info(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckTCP ERROR: Bad or wrong arguments: " + e.getMessage());
            return reply;
        }

        if (port == 0 || port > 65535) {
            CheckTCP.LOGGER.info("Bad port definition: " + port);
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckTCP ERROR: Bad port definition " + port);
            return reply;
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            CheckTCP.LOGGER.info(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckTCP ERROR: Host lookup fail for: " + host + " " + e.getMessage());
            return reply;
        }

        Socket sock = new Socket();
        long start;
        long stop;
        try {
            start = System.nanoTime();
            sock.connect(new InetSocketAddress(addr, port), msTimeout);
            stop = System.nanoTime();
            sock.close();
        } catch (IOException e) {
            reply.setReply("CheckTCP " + refuseState.toString() + " " + e.getMessage());
            reply.setStatus(refuseState);
            return reply;
        }

        long elapsed = (stop - start) / 1000000;
        reply.putPerformance("ReplyDuration", elapsed);
        Status st;
        if (Status.OK.equals(acceptState)) {
            if (elapsed >= msCritical) {
                st = Status.CRITICAL;
            } else if (elapsed >= msWarning) {
                st = Status.WARNING;
            } else {
                st = Status.OK;
            }
        } else {
            st = acceptState;
        }
        reply.setStatus(st);
        reply.setReply("CheckTCP " + st + " Time elapsed: " + elapsed + " milliseconds");
        return reply;
    }
}
