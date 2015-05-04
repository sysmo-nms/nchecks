/* Copyright (C) 2014, Sebastien Serre <sserre.bx@gmail.com>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.sysmo.nchecks.modules;

import io.sysmo.nchecks.NChecksInterface;
import io.sysmo.nchecks.Argument;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Const;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;

import java.util.Map;
import java.util.Arrays;
import java.net.InetAddress;


public class CheckICMP implements NChecksInterface
{
    private String  host        = "";
    private int     pktsNumber  = 5;
    private int     pktsSize    = 56;
    private int     plWarning   = 50;
    private int     plCritical  = 100;
    private int     msWarning   = 200;
    private int     msCritical  = 2500;
    private int     msTimeout   = 5000;
    private int     msInterval  = 100;
    private String  useIpv6     = "false";
    private String  stdoutReturn;
    private String  stderrReturn;
    private static  String  pping;

    public static void setPping(String com)
    {
        pping = com;
    }

    public CheckICMP() {}

    public void setReturnString(String returnString, String returnStream)
    {
        if ("stdout".equals(returnStream))
        {
            this.stdoutReturn = returnString;
        }
        else if ("stderr".equals(returnStream))
        {
            this.stderrReturn = returnString;
        }
    }

    public Reply execute(Query query)
    {
        Reply reply = new Reply();

        Argument hostArg     = query.get("host");
        Argument numberArg   = query.get("pkts_number");
        Argument sizeArg     = query.get("pkts_size");
        Argument plWarnArg   = query.get("pl_warning");
        Argument plCritArg   = query.get("pl_critical");
        Argument msWarnArg   = query.get("ms_warning");
        Argument msCritArg   = query.get("ms_critical");
        Argument timeoutArg  = query.get("ms_timeout");
        Argument intervalArg = query.get("ms_interval");
        Argument useV6Arg    = query.get("useIpv6");

        try {
            if (hostArg     != null) { this.host        = hostArg.asString(); }
            if (numberArg   != null) { this.pktsNumber  = numberArg.asInteger(); }
            if (sizeArg     != null) { this.pktsSize    = sizeArg.asInteger(); }
            if (plWarnArg   != null) { this.plWarning   = plWarnArg.asInteger(); }
            if (plCritArg   != null) { this.plCritical  = plCritArg.asInteger(); }
            if (msWarnArg   != null) { this.msWarning   = msWarnArg.asInteger(); }
            if (msCritArg   != null) { this.msCritical  = msCritArg.asInteger(); }
            if (timeoutArg  != null) { this.msTimeout   = timeoutArg.asInteger(); }
            if (intervalArg != null) { this.msInterval  = intervalArg.asInteger(); }
            if (useV6Arg    != null) { this.useIpv6     = useV6Arg.asString(); }
        } catch (Exception|Error e) {
            reply.setStatus(Const.STATUS_ERROR);
            reply.setReply("Missing argument or argument type is wrong: " + e);
            return reply;
        }




        if ("".equals(host)) {
            reply.setStatus(Const.STATUS_DOWN);
            reply.setReply("Host must be specified");
            return reply;
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
            this.host = addr.getHostAddress();
        } catch (Exception e) {
            reply.setStatus(Const.STATUS_DOWN);
            reply.setReply("Host lookup fail for: " + host);
            return reply;
        }

        String[] cmd;
        String[] args = new String[7];
        args[0] = pping;
        args[1] = "--host="     + this.host;
        args[2] = "--interval=" + this.msInterval;
        args[3] = "--ipv6="     + this.useIpv6;
        args[4] = "--number="   + this.pktsNumber;
        args[5] = "--size="     + this.pktsSize;
        args[6] = "--timeout="  + this.msTimeout;

        int returnStatus;
        Process proc;
        try {
            String osName = System.getProperty("os.name");

            if (osName.startsWith("Windows"))
            {
                cmd = new String[args.length + 2];
                if (osName.equals("Windows 95")) {
                    cmd[0] = "command.com";
                } else {
                    cmd[0] = "cmd.exe";
                }
                cmd[1] = "/C";
                System.arraycopy(args, 0, cmd, 2, args.length);
            }
            else if (osName.equals("Linux"))
            {
                cmd = new String[3];
                cmd[0] = "/bin/sh";
                cmd[1] = "-c";
                cmd[2] = args[0] + " " + args[1];
            }
            else
            {
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

        } catch (Exception e) {
            e.printStackTrace();
            reply.setStatus(Const.STATUS_ERROR);
            String errorMsg = e + e.getMessage();
            reply.setReply(errorMsg);
            return reply;
        }

        if (returnStatus != 0) {
            reply.setStatus(Const.STATUS_ERROR);
            reply.setReply("CheckICMP ERROR: " + stdoutReturn + stderrReturn);
            return reply;
        }


        String[] ppingReturn = stdoutReturn.split(",");
        if (! "<PPING_RETURN>".equals(ppingReturn[0])) {
            reply.setStatus(Const.STATUS_DOWN);
            reply.setReply(String.format("CheckICMP ERROR: stdout=%s, stderr=%s", stderrReturn, stdoutReturn));
            return reply;
        } 

        long percentLoss  = Long.parseLong(ppingReturn[1]);
        long minReplyTime = Long.parseLong(ppingReturn[2]);
        long avgReplyTime = Long.parseLong(ppingReturn[3]);
        long maxReplyTime = Long.parseLong(ppingReturn[4]);
        reply.putPerformance("PercentLost",         percentLoss);
        reply.putPerformance("MinRoundTrip",        minReplyTime);
        reply.putPerformance("AverageRoundTrip",    avgReplyTime);
        reply.putPerformance("MaxRoundTrip",        maxReplyTime);

        String st = null;
        if (percentLoss == 100) {
            st = Const.STATUS_DOWN;
        }else if (percentLoss >= plCritical || avgReplyTime >= msCritical) {
            st = Const.STATUS_CRITICAL;
        } else if (percentLoss >= plWarning || avgReplyTime >= msWarning) {
            st = Const.STATUS_WARNING;
        } else {
            st = Const.STATUS_OK;
        }
        String formated = String.format(
            "CheckICMP %s: PktsLoss=%d%%, RTMin=%d, RTAverage=%d, RTMax=%d",
            st, percentLoss, minReplyTime, avgReplyTime, maxReplyTime
        );
        reply.setStatus(st);
        reply.setReply(formated);

        return reply;
    }
}

class StreamConsumer extends Thread {
    private InputStream     is;
    private String          type;
    private StringBuffer    replyString;
    private CheckICMP       parent;

    public StreamConsumer(
            InputStream inputStream,
            String      type,
            CheckICMP   parent)
    {
        this.is     = inputStream;
        this.type   = type;
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
            e.printStackTrace();
        } finally {
            if(buff != null) {
                try { buff.close(); }
                catch(Exception ignore) {
                    System.out.println("failed to close the buffer for ICMP");
                }
            }
        }
    }
}