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
import io.sysmo.nchecks.snmp.Manager;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Status;
import io.sysmo.nchecks.states.PerformanceGroupState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.AbstractTarget;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Definition of the check is in the file CheckIfXBroadcast.xml
 */
public class CheckIfXBroadcast implements CheckInterface
{
    private static Logger logger = LoggerFactory.getLogger(CheckIfXBroadcast.class);
    private static String IF_INDEX         = "1.3.6.1.2.1.2.2.1.1";
    private static String IF_IN_BROADCAST  = "1.3.6.1.2.1.31.1.1.1.3";
    private static String IF_OUT_BROADCAST = "1.3.6.1.2.1.31.1.1.1.5";

    private static OID[] columns = new OID[]{
            new OID(IF_INDEX),
            new OID(IF_IN_BROADCAST),
            new OID(IF_OUT_BROADCAST)
    };

    public CheckIfXBroadcast() {}

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
            CheckIfXBroadcast.logger.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Missing or wrong argument: " + e);
            return reply;
        }

        PerformanceGroupState state = (PerformanceGroupState) query.getState();
        if (state == null) {
            state = new PerformanceGroupState();
        }

        try {

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

            // TODO try PDU.GETBULK then PDU.GETNEXT to degrade....
            // TODO keep degrade state in reply.setState(v)
            AbstractTarget target = Manager.getTarget(query);
            TableUtils tableWalker = Manager.getTableUtils(state.getPduType());
            List<TableEvent> snmpReply = tableWalker.getTable(
                    target, CheckIfXBroadcast.columns,
                    lowerBoundIndex, upperBoundIndex);

            // TODO check the last element of the list see TableUtils.getTable
            // and TableEvent.getStatus()

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
                    Long broadcastIn = vbs[1].getVariable().toLong();
                    Long broadcastOut = vbs[2].getVariable().toLong();
                    reply.putPerformance(ifIndex, "IfOutBroadcastPkts", broadcastIn);
                    reply.putPerformance(ifIndex, "IfInBroadcastPkts", broadcastOut);

                    state.put(ifIndex, broadcastIn + broadcastOut);
                }
            }

            Status newStatus = state.computeStatusMaps(
                    warningThreshold, criticalThreshold);

            String replyMsg;
            if (newStatus.equals(Status.OK)) {
                replyMsg = "CheckIfXBroadcast OK";
            } else if (newStatus.equals(Status.UNKNOWN)) {
                replyMsg = "CheckIfXBroadcast UNKNOWN. No enough data to set sensible status.";
            } else if (newStatus.equals(Status.WARNING)) {
                replyMsg = "CheckIfXBroadcast WARNING have found errors!";
            } else if (newStatus.equals(Status.CRITICAL)) {
                replyMsg = "CheckIfXBroadcast CRITICAL have found errors!";
            } else {
                replyMsg = "";
            }

            reply.setState(state);
            reply.setStatus(newStatus);
            reply.setReply(replyMsg);
            return reply;
        } catch (Exception|Error e) {
            CheckIfXBroadcast.logger.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Error: " + error);
            return reply;
        }
    }
}
