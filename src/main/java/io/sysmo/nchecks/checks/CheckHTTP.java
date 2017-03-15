/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.sysmo.nchecks.checks;

import io.sysmo.nchecks.CheckInterface;
import io.sysmo.nchecks.Argument;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Status;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author seb
 */
public class CheckHTTP implements CheckInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckTCP.class);

    public CheckHTTP() {

    }

    @Override
    public Reply execute(Query query) {
        Reply reply = new Reply();
        Argument uri_arg = query.get("uri");
        Argument ok_status_arg = query.get("ok_status");
        Argument method_arg = query.get("method");
        Argument follow_arg = query.get("follow_redirect");

        String uri = null;
        String ok_status = "2**";
        String method = "GET";
        boolean follow_directive = true;

        try {
            if (uri_arg != null) {
                uri = uri_arg.asString();
            }
            if (ok_status_arg != null) {
                ok_status = ok_status_arg.asString();
            }
            if (method_arg != null) {
                method = method_arg.asString();
            }

            if (follow_arg != null) {
                String follow = follow_arg.asString();
                if (follow.equals("false")) {
                    follow_directive = false;
                }
            }
        } catch (Exception | Error e) {
            CheckHTTP.LOGGER.info(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckHTTP ERROR: Bad arguments: " + e.getMessage());
            return reply;
        }

        URL urlobj;
        try {
            urlobj = new URL(uri);
        } catch (MalformedURLException e) {
            CheckHTTP.LOGGER.info(e.getMessage(), e);
            CheckHTTP.LOGGER.info(uri);
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckHTTP ERROR: " + e.getMessage());
            return reply;
        }

        HttpURLConnection con;
        try {
            con = (HttpURLConnection) urlobj.openConnection();
        } catch (IOException e) {
            CheckHTTP.LOGGER.info(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckHTTP ERROR " + e.getMessage());
            return reply;
        }

        con.setRequestProperty("User-Agent", "NChecks CheckHTTP/1.0");
        con.setInstanceFollowRedirects(follow_directive);
        if (!"".equals(method)) {
            try {
                con.setRequestMethod(method);
            } catch (ProtocolException e) {
                CheckHTTP.LOGGER.info(e.getMessage(), e);
                reply.setStatus(Status.ERROR);
                reply.setReply("CheckHTTP ERROR " + e.getMessage());
                return reply;
            }
        }

        long start;
        long stop;
        int responseCode;
        try {
            start = System.nanoTime();
            responseCode = con.getResponseCode();
            stop = System.nanoTime();
        } catch (IOException e) {
            CheckHTTP.LOGGER.info(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckHTTP ERROR " + e.getMessage());
            return reply;
        }

        long elapsed = (stop - start) / 1000000;
        reply.putPerformance("ReplyDuration", elapsed);
        try {
            if (compareStatuses(responseCode, ok_status)) {
                reply.setStatus(Status.OK);
                reply.setReply("CheckHTTP OK, response code is " + responseCode);
                return reply;
            } else {
                reply.setStatus(Status.CRITICAL);
                reply.setReply("CheckHTTP CRITICAL, bad response code: " + responseCode);
                return reply;
            }

        } catch (Exception e) {
            CheckHTTP.LOGGER.info(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("CheckHTTP ERROR " + e.getMessage());
            return reply;
        }
    }

    private static boolean compareStatuses(int code, String arg_code) throws Exception {

        String str_code = String.valueOf(code);
        char cmp1;
        char cmp2;

        // Check the X.. char
        cmp1 = str_code.charAt(0);
        cmp2 = arg_code.charAt(0);

        if (CheckHTTP.compareChars(cmp2, cmp1) == false) {
            return false;
        }

        // Check the .X. char
        cmp1 = str_code.charAt(1);
        cmp2 = arg_code.charAt(1);

        if (CheckHTTP.compareChars(cmp2, cmp1) == false) {
            return false;
        }

        // Check the ..X char
        cmp1 = str_code.charAt(1);
        cmp2 = arg_code.charAt(1);

        return CheckHTTP.compareChars(cmp2, cmp1);
    }

    private static boolean compareChars(char a, char b) {
        if (a == '*') {
            return true;
        }
        return a == b;
    }
}
