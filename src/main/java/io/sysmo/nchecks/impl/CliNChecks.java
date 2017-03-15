/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.sysmo.nchecks.impl;

import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Argument;
import io.sysmo.nchecks.snmp.Manager;
import io.sysmo.nchecks.CheckInterface;
import io.sysmo.nchecks.checks.*;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author seb
 */
public class CliNChecks {

    static Logger LOGGER = LoggerFactory.getLogger(CliNChecks.class);

    public static void main(String[] args) {

        try {
            if (args[0].equals("--snmp")) {
                LOGGER.info("Will start with SNMP");
                Manager.start("/tmp");
            } else {
                LOGGER.info("Will start without SNMP");
            }

            CliNChecks.launch(args);
        } catch (Exception e) {
            LOGGER.info("Exception: ", e);
            System.exit(1);
        }

    }

    private static void launch(String[] args) throws Exception {

        CheckInterface check = (CheckInterface) Class.forName(
                "io.sysmo.nchecks.checks." + args[1]).newInstance();

        String[] parts;
        Map<String, Argument> query_args = new HashMap<>();
        for (int i = 2; i < args.length; i++) {
            parts = args[i].split("=");
            query_args.put(parts[0], new Argument(parts[1]));
        }

        Query query = new Query(query_args);
        Reply reply = check.execute(query);
        LOGGER.info(reply.toString());

    }
}
