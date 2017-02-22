/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.sysmo.nchecks.impl;

import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Argument;
import io.sysmo.nchecks.CheckInterface;
import io.sysmo.nchecks.checks.CheckHTTP;
import java.util.Map;
import java.util.HashMap;

/**
 *
 * @author seb
 */
public class CommandLine {

    public static void main(String[] args) {
        try {
            CommandLine.testNChecksTcp();
        } catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    private static void testNChecksTcp() throws Exception {
        CheckInterface check;
        check = new CheckHTTP();

        Argument a1 = new Argument();
        Argument a2 = new Argument();
        Argument a3 = new Argument();

        a1.set("http://www.google.fr");
        a2.set("POST");
        a3.set("4**");
        Map<String, Argument> query_args = new HashMap<>();
        query_args.put("uri", a1);
        query_args.put("method", a2);
        query_args.put("ok_status", a3);

        Query query = new Query(query_args);
        Reply reply = check.execute(query);
        System.out.println(reply.getReply());
    }
}
