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

package io.sysmo.nchecks.modules;

import io.sysmo.nchecks.HelperInterface;
import io.sysmo.nchecks.HelperReply;
import io.sysmo.nchecks.NChecksInterface;
import io.sysmo.nchecks.NChecksSNMP;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.helpers.GetIfTableHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.AbstractTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Definition of the check is in the file CheckIfErrors.xml
 */
public class CheckIfErrors implements NChecksInterface, HelperInterface
{
    static Logger logger = LoggerFactory.getLogger(CheckIfErrors.class);
    private static String IF_INDEX      = "1.3.6.1.2.1.2.2.1.1";
    private static String IF_IN_ERRORS  = "1.3.6.1.2.1.2.2.1.14";
    private static String IF_OUT_ERRORS = "1.3.6.1.2.1.2.2.1.20";

    private static OID[] columns = new OID[]{
            new OID(IF_INDEX),
            new OID(IF_IN_ERRORS),
            new OID(IF_OUT_ERRORS)
    };

    public CheckIfErrors() {}

    public Reply execute(Query query)
    {
        Reply  reply = new Reply();
        String error = "undefined";
        String ifSelection;
        int warningThreshold;
        int criticalThreshold;

        try {
            ifSelection = query.get("if_selection").asString();
            warningThreshold = query.get("warning_threshold").asInteger();
            criticalThreshold = query.get("critical_threshold").asInteger();
        } catch (Exception|Error e) {
            CheckIfErrors.logger.error(e.getMessage(), e);
            reply.setStatus(Reply.STATUS_ERROR);
            reply.setReply("Missing or wrong argument: " + e);
            return reply;
        }

        IfErrorsState state = (IfErrorsState) query.getState();
        if (state == null) {
            state = new IfErrorsState();
        }

        HashMap<Integer, Long> newStatusMap = new HashMap<>();
        try {
            AbstractTarget target = NChecksSNMP.getInstance().getTarget(query);

            Snmp session = NChecksSNMP.getInstance().getSnmpSession();

            // TODO try PDU.GETBULK then PDU.GETNEXT to degrade....
            // TODO keep degrade state in reply.setState(v)
            TableUtils tableWalker =
                    new TableUtils(session, new DefaultPDUFactory(state.getPduType()));


            // get indexes string list
            String[] indexesArrayString = ifSelection.split(",");

            // transform to index array int
            Integer[] indexesArrayInt = new Integer[indexesArrayString.length];
            for (int i = 0; i < indexesArrayString.length; i++) {
                indexesArrayInt[i] = Integer.parseInt(indexesArrayString[i]);
            }

            // sort it
            Arrays.sort(indexesArrayInt);

            // build upper and lower bound indexes
            Integer lower = indexesArrayInt[0] - 1;
            Integer upper = indexesArrayInt[indexesArrayInt.length - 1];
            OID lowerBoundIndex = new OID(lower.toString());
            OID upperBoundIndex = new OID(upper.toString());

            List<TableEvent> snmpReply = tableWalker.getTable(
                    target, CheckIfErrors.columns,
                    lowerBoundIndex, upperBoundIndex);

            // TODO degrade to PDU.GETNEXT if some vb(s) == null
            // TODO check if reply is valid. Where is is the error status?

            // asList for List.contains
            List<Integer> intList = Arrays.asList(indexesArrayInt);
            Iterator<TableEvent> it = snmpReply.iterator();
            TableEvent evt;
            while (it.hasNext()) {
                evt = it.next();
                error = evt.getErrorMessage();
                VariableBinding[]   vbs = evt.getColumns();
                Integer ifIndex = vbs[0].getVariable().toInt();

                if (intList.contains(ifIndex)) {
                    Long errsIn = vbs[1].getVariable().toLong();
                    Long errsOut = vbs[2].getVariable().toLong();
                    reply.putPerformance(ifIndex, "IfInErrors", errsIn);
                    reply.putPerformance(ifIndex, "IfOutErrors", errsOut);

                    newStatusMap.put(ifIndex, errsIn + errsOut);
                }
            }

            String newStatus = state.computeStatusMaps(
                    newStatusMap, warningThreshold, criticalThreshold);

            String replyMsg;
            switch (newStatus) {
                case Reply.STATUS_OK:
                    replyMsg = "CheckIfErrors OK";
                    break;
                case Reply.STATUS_UNKNOWN:
                    replyMsg = "CheckIfErrors UNKNOWN. No enough data to compute thresholds";
                    break;
                case Reply.STATUS_CRITICAL:
                    replyMsg = "CheckIfErrors CRITICAL have found errors!";
                    break;
                case Reply.STATUS_WARNING:
                    replyMsg = "CheckIfErrors WARNING have found errors!";
                    break;
                default:
                    replyMsg = "";
            }
            reply.setState(state);
            reply.setStatus(newStatus);
            reply.setReply(replyMsg);
            return reply;
        } catch (Exception|Error e) {
            CheckIfErrors.logger.error(e.getMessage(), e);
            reply.setStatus(Reply.STATUS_ERROR);
            reply.setReply("Error: " + error);
            return reply;
        }
    }


    /*
     * Helper interface
     */
    public HelperReply callHelper(Query query, String id)
    {
        GetIfTableHelper helper = new GetIfTableHelper();
        return helper.call(query);
    }

    static class IfErrorsState implements Serializable {
        private int pduType;
        private Date time;
        private HashMap<Integer, Long> data;
        private String status;

        IfErrorsState() {
            this.pduType = PDU.GETNEXT;
            this.time    = new Date();
            this.data    = new HashMap<>();
            this.status  = Reply.STATUS_UNKNOWN;
        }

        public int getPduType() {
            return this.pduType;
        }

        public String computeStatusMaps(HashMap<Integer,Long> update,
                int warning, int critical)
        {
            Date newDate = new Date();
            Date oldDate = this.time;
            long seconds = (newDate.getTime() - oldDate.getTime()) / 1000;
            long minutes;
            boolean keepWorstState = false;
            if (seconds < 60) {
                // no enough time elapsed return old status or worst status
                keepWorstState = true;
                minutes = 1;
            } else {
                minutes = seconds / 60;
            }

            String status = Reply.STATUS_OK;
            for (Map.Entry<Integer, Long> entry: update.entrySet())
            {
                Integer key = entry.getKey();
                Long upd = entry.getValue();
                Long old = this.data.get(key);
                if (old != null) {
                    long diff = (upd - old) / minutes;
                    if (diff > warning) {
                        status = Reply.STATUS_WARNING;
                    }
                    if (diff > critical) {
                        status = Reply.STATUS_CRITICAL;
                    }
                }
            }

            if (keepWorstState) {
                switch (status) {
                    case Reply.STATUS_CRITICAL:
                        // it is the worst
                        this.status = status;
                        break;
                    case Reply.STATUS_WARNING:
                        // is it the worst?
                        if (!this.status.equals(Reply.STATUS_CRITICAL)) {
                            // yes go warning
                            this.status = status;
                        }
                        break;
                    case Reply.STATUS_OK:
                        // is it the worst?
                        if (this.status.equals(Reply.STATUS_CRITICAL)) {
                            // no stay with our critical
                            break;
                        } else if (this.status.equals(Reply.STATUS_WARNING)) {
                            // no stay with our warning
                            break;
                        } else if (this.status.equals(Reply.STATUS_UNKNOWN)) {
                            // no stay with our unknown
                            break;
                        } else {
                            // then go ok
                            this.status = status;
                            break;
                        }
                }
            } else {
                this.time = newDate;
                this.data = update;
                this.status = status;
            }
            return this.status;
        }
    }
}
