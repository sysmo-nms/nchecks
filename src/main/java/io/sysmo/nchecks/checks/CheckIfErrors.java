/*
 * Sysmo NMS Network Management and Monitoring solution (http://www.sysmo.io)
 *
 * Copyright (c) 2012-2015 Sebastien Serre <ssbx@sysmo.io>
 *
 * This file is part of Sysmo NMS.
 *
 * Sysmo NMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sysmo NMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Sysmo.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.sysmo.nchecks.checks;

import io.sysmo.nchecks.CheckInterface;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.snmp.TableWalker;
import io.sysmo.nchecks.states.PerformanceGroupState;

import io.sysmo.nchecks.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.TableEvent;

import java.util.Iterator;
import java.util.List;


/**
 * Definition of the check is in the file CheckIfErrors.xml
 */
public class CheckIfErrors implements CheckInterface {
    static Logger logger = LoggerFactory.getLogger(CheckIfErrors.class);
    private static String IF_INDEX = "1.3.6.1.2.1.2.2.1.1";
    private static String IF_IN_ERRORS = "1.3.6.1.2.1.2.2.1.14";
    private static String IF_OUT_ERRORS = "1.3.6.1.2.1.2.2.1.20";

    private static OID[] columns = new OID[]{
            new OID(IF_INDEX),
            new OID(IF_IN_ERRORS),
            new OID(IF_OUT_ERRORS)
    };

    public CheckIfErrors() {
    }

    public Reply execute(Query query) {
        Reply reply = new Reply();
        String error = "undefined";
        String ifSelection;
        int warningThreshold;
        int criticalThreshold;

        try {
            ifSelection = query.get("if_selection").asString();
            warningThreshold = query.get("warning_threshold").asInteger();
            criticalThreshold = query.get("critical_threshold").asInteger();
        } catch (Exception | Error e) {
            CheckIfErrors.logger.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Missing or wrong argument: " + e);
            return reply;
        }

        String[] indexesArrayString = ifSelection.split(",");

        PerformanceGroupState state = (PerformanceGroupState) query.getState();
        if (state == null) {
            state = new PerformanceGroupState();
        }

        TableWalker walker = new TableWalker();
        walker.addColumn(IF_INDEX);
        walker.addColumn(IF_IN_ERRORS);
        walker.addColumn(IF_OUT_ERRORS);
        for (String index: indexesArrayString) {
            walker.addIndex(index);
        }

        try {


            List<TableEvent> snmpReply = walker.walk(query);

            // TODO check the last element of the list see TableUtils.getTable
            // and TableEvent.getStatus()

            // asList for List.contains
            List<Integer> intList = walker.getIndexes();

            Iterator<TableEvent> it = snmpReply.iterator();
            TableEvent evt;
            while (it.hasNext()) {
                evt = it.next();
                error = evt.getErrorMessage();
                VariableBinding[] vbs = evt.getColumns();
                Integer ifIndex = vbs[0].getVariable().toInt();

                if (intList.contains(ifIndex)) {
                    Long errsIn = vbs[1].getVariable().toLong();
                    Long errsOut = vbs[2].getVariable().toLong();
                    reply.putPerformance(ifIndex, "IfInErrors", errsIn);
                    reply.putPerformance(ifIndex, "IfOutErrors", errsOut);

                    state.put(ifIndex, errsIn + errsOut);
                }
            }

            Status newStatus = state.computeStatusMaps(
                    warningThreshold, criticalThreshold);

            String replyMsg;
            if (newStatus.equals(Status.OK)) {
                replyMsg = "CheckIfErrors OK";
            } else if (newStatus.equals(Status.UNKNOWN)) {
                replyMsg = "CheckIfErrors UNKNOWN. No enough data to set sensible status.";
            } else if (newStatus.equals(Status.WARNING)) {
                replyMsg = "CheckIfErrors WARNING have found errors!";
            } else if (newStatus.equals(Status.CRITICAL)) {
                replyMsg = "CheckIfErrors CRITICAL have found errors!";
            } else {
                replyMsg = "";
            }

            reply.setState(state);
            reply.setStatus(newStatus);
            reply.setReply(replyMsg);
            return reply;
        } catch (Exception | Error e) {
            CheckIfErrors.logger.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Error: " + error + ". " + e.getMessage());
            return reply;
        }
    }
}

