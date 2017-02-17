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
import io.sysmo.nchecks.checks.CheckTCP;
import java.util.Map;
import java.util.HashMap;

/**
 *
 * @author seb
 */
public class CommandLine {

    public static void main(String[] args) {
        /* TODO use args info */
        try {
            CommandLine.testNChecksTcp();
        } catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    private static void testNChecksTcp() throws Exception {
        CheckInterface check;
        check = new CheckTCP();

        Argument a1 = new Argument();
        Argument a2 = new Argument();

        a1.set("localhost");
        a2.set("9000");

        Map<String, Argument> query_args = new HashMap<>();
        query_args.put("hostname", a1);
        query_args.put("port", a2);

        Query query = new Query(query_args);
        Reply reply = check.execute(query);
        System.out.println(reply.getReply());
    }
}
