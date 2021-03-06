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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.file.FileSystems;

import java.net.InetAddress;

import io.sysmo.nchecks.Status;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckICMP implements CheckInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckICMP.class);

    private String host = "";
    private int pktsNumber = 5;
    private int pktsSize = 56;
    private int plWarning = 50;
    private int plCritical = 100;
    private int msWarning = 200;
    private int msCritical = 2500;
    private int msTimeout = 5000;
    private int msInterval = 100;
    private String useIpv6 = "false";
    private String stdoutReturn;
    private String stderrReturn;
    private static String pping;

    public static void setPping(String utilsDir) {

        String utilsPath = FileSystems
                .getDefault()
                .getPath(utilsDir)
                .toAbsolutePath()
                .toString();

        String ppingExe;
        if (System.getProperty("os.name").contains("Windows")) {
            ppingExe = "pping.exe";
        } else {
            ppingExe = "pping";
        }

        pping = FileSystems
                .getDefault()
                .getPath(utilsPath, ppingExe)
                .toString();
    }

    public CheckICMP() {
    }

    private void setReturnString(String returnString, String returnStream) {
        if ("stdout".equals(returnStream)) {
            this.stdoutReturn = returnString;
        } else if ("stderr".equals(returnStream)) {
            this.stderrReturn = returnString;
        }
    }

    @Override
    public Reply execute(Query query) {
        Reply reply = new Reply();

        Argument hostArg = query.get("host");
        Argument numberArg = query.get("pkts_number");
        Argument sizeArg = query.get("pkts_size");
        Argument plWarnArg = query.get("pl_warning");
        Argument plCritArg = query.get("pl_critical");
        Argument msWarnArg = query.get("ms_warning");
        Argument msCritArg = query.get("ms_critical");
        Argument timeoutArg = query.get("ms_timeout");
        Argument intervalArg = query.get("ms_interval");
        Argument useV6Arg = query.get("useIpv6");

        try {
            if (hostArg != null) {
                this.host = hostArg.asString();
            }
            if (numberArg != null) {
                this.pktsNumber = numberArg.asInteger();
            }
            if (sizeArg != null) {
                this.pktsSize = sizeArg.asInteger();
            }
            if (plWarnArg != null) {
                this.plWarning = plWarnArg.asInteger();
            }
            if (plCritArg != null) {
                this.plCritical = plCritArg.asInteger();
            }
            if (msWarnArg != null) {
                this.msWarning = msWarnArg.asInteger();
            }
            if (msCritArg != null) {
                this.msCritical = msCritArg.asInteger();
            }
            if (timeoutArg != null) {
                this.msTimeout = timeoutArg.asInteger();
            }
            if (intervalArg != null) {
                this.msInterval = intervalArg.asInteger();
            }
            if (useV6Arg != null) {
                this.useIpv6 = useV6Arg.asString();
            }
        } catch (Exception | Error e) {
            CheckICMP.LOGGER.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Missing argument or argument type is wrong: " + e);
            return reply;
        }

        if ("".equals(host)) {
            reply.setStatus(Status.ERROR);
            reply.setReply("Host must be specified");
            return reply;
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
            this.host = addr.getHostAddress();
        } catch (UnknownHostException e) {
            CheckICMP.LOGGER.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Host lookup fail for: " + host);
            return reply;
        }

        String[] cmd;
        String[] args = new String[2];
        args[0] = pping;
        args[1] = "--host=" + this.host;
        //args[2] = "--interval=" + this.msInterval;
        //args[3] = "--ipv6="     + this.useIpv6;
        //args[4] = "--number="   + this.pktsNumber;
        //args[5] = "--size="     + this.pktsSize;
        //args[6] = "--timeout="  + this.msTimeout;

        int returnStatus;
        Process proc;
        try {
            String osName = System.getProperty("os.name");

            if (osName.startsWith("Windows")) {
                cmd = new String[args.length + 2];
                if (osName.equals("Windows 95")) {
                    cmd[0] = "command.com";
                } else {
                    cmd[0] = "cmd.exe";
                }
                cmd[1] = "/C";
                System.arraycopy(args, 0, cmd, 2, args.length);
            } else if (osName.equals("Linux")) {
                cmd = new String[3];
                cmd[0] = "/bin/sh";
                cmd[1] = "-c";
                cmd[2] = args[0] + " " + args[1];
            } else {
                cmd = args;
            }

            Runtime runtime = Runtime.getRuntime();
            proc = runtime.exec(cmd);
            StreamConsumer stdoutConsumer = new StreamConsumer(proc.getInputStream(), "stdout", this);
            StreamConsumer stderrConsumer = new StreamConsumer(proc.getErrorStream(), "stderr", this);
            stdoutConsumer.start();
            stderrConsumer.start();

            returnStatus = proc.waitFor();
            stdoutConsumer.join();
            stderrConsumer.join();

        } catch (InterruptedException | IOException e) {
            CheckICMP.LOGGER.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            String errorMsg = e + e.getMessage();
            reply.setReply(errorMsg);
            return reply;
        }

        if (returnStatus != 0) {
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckICMP ERROR: " + stdoutReturn + stderrReturn);
            return reply;
        }

        String[] ppingReturn = stdoutReturn.split(",");
        if (!"<PPING_RETURN>".equals(ppingReturn[0])) {
            reply.setStatus(Status.ERROR);
            reply.setReply(String.format("CheckICMP ERROR: stdout=%s, stderr=%s", stderrReturn, stdoutReturn));
            return reply;
        }

        long percentLoss = Long.parseLong(ppingReturn[1]);
        long minReplyTime = Long.parseLong(ppingReturn[2]);
        long avgReplyTime = Long.parseLong(ppingReturn[3]);
        long maxReplyTime = Long.parseLong(ppingReturn[4]);
        reply.putPerformance("PercentLost", percentLoss);
        reply.putPerformance("MinRoundTrip", minReplyTime);
        reply.putPerformance("AverageRoundTrip", avgReplyTime);
        reply.putPerformance("MaxRoundTrip", maxReplyTime);

        Status st;
        if (percentLoss == 100) {
            st = Status.CRITICAL;
        } else if (percentLoss >= plCritical || avgReplyTime >= msCritical) {
            st = Status.CRITICAL;
        } else if (percentLoss >= plWarning || avgReplyTime >= msWarning) {
            st = Status.WARNING;
        } else {
            st = Status.OK;
        }
        String formated = String.format(
                "CheckICMP %s: PktsLoss=%d%%, RTMin=%d, RTAverage=%d, RTMax=%d",
                st, percentLoss, minReplyTime, avgReplyTime, maxReplyTime
        );
        reply.setStatus(st);
        reply.setReply(formated);

        return reply;
    }
    // utility classes

    static private class StreamConsumer extends Thread {

        private final InputStream is;
        private final String type;
        private final StringBuffer replyString;
        private final CheckICMP parent;

        StreamConsumer(
                InputStream inputStream,
                String type,
                CheckICMP parent) {
            this.is = inputStream;
            this.type = type;
            this.replyString = new StringBuffer();
            this.parent = parent;
        }

        @Override
        public void run() {
            BufferedReader buff = null;
            try {
                buff = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;

                while ((line = buff.readLine()) != null) {
                    replyString.append(line);
                }
                this.parent.setReturnString(replyString.toString(), type);
            } catch (IOException e) {
                CheckICMP.LOGGER.error(e.getMessage(), e);
            } finally {
                if (buff != null) {
                    try {
                        buff.close();
                    } catch (IOException ignore) {
                        CheckICMP.LOGGER.error("failed to close the buffer for ICMP");
                    }
                }
            }
        }
    }
}
