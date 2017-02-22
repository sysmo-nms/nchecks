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
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.snmp.Manager;

import io.sysmo.nchecks.Status;
import org.snmp4j.AbstractTarget;
import org.snmp4j.util.TableUtils;
import org.snmp4j.util.TableEvent;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.PDU;

import java.util.Arrays;
import java.util.List;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckNetworkInterfaces implements CheckInterface {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(CheckNetworkInterfaces.class);
    private static final String IF_INDEX = "1.3.6.1.2.1.2.2.1.1";
    private static final String IF_IN_OCTETS = "1.3.6.1.2.1.2.2.1.10";
    private static final String IF_IN_UCASTPKTS = "1.3.6.1.2.1.2.2.1.11";
    private static final String IF_IN_NUCASTPKTS = "1.3.6.1.2.1.2.2.1.12";
    private static final String IF_IN_ERRORS = "1.3.6.1.2.1.2.2.1.14";
    private static final String IF_OUT_OCTETS = "1.3.6.1.2.1.2.2.1.16";
    private static final String IF_OUT_UCASTPKTS = "1.3.6.1.2.1.2.2.1.17";
    private static final String IF_OUT_NUCASTPKTS = "1.3.6.1.2.1.2.2.1.18";
    private static final String IF_OUT_ERRORS = "1.3.6.1.2.1.2.2.1.20";

    private static final OID[] COLUMNS = new OID[]{
        new OID(IF_INDEX),
        new OID(IF_IN_OCTETS),
        new OID(IF_IN_UCASTPKTS),
        new OID(IF_IN_NUCASTPKTS),
        new OID(IF_IN_ERRORS),
        new OID(IF_OUT_OCTETS),
        new OID(IF_OUT_UCASTPKTS),
        new OID(IF_OUT_NUCASTPKTS),
        new OID(IF_OUT_ERRORS)
    };

    public CheckNetworkInterfaces() {
    }

    @Override
    public Reply execute(Query query) {
        Reply reply = new Reply();
        String error = "undefined";
        String ifSelection;

        try {
            ifSelection = query.get("if_selection").asString();
        } catch (Exception | Error e) {
            CheckNetworkInterfaces.LOGGER.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Missing or wrong argument: " + e);
            return reply;
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
            // TODO keep degrade state in reply.setOpaqueData(v)
            AbstractTarget target = Manager.getTarget(query);
            TableUtils tableWalker = Manager.getTableUtils(PDU.GETNEXT);
            List<TableEvent> snmpReply = tableWalker.getTable(
                    target, CheckNetworkInterfaces.COLUMNS,
                    lowerBoundIndex, upperBoundIndex);

            // TODO degrade to PDU.GETNEXT if some vb(s) == null
            // TODO check if reply is valid. Where is is the error status?
            List<Integer> intList = Arrays.asList(indexesArrayInt);
            Iterator<TableEvent> it = snmpReply.iterator();
            TableEvent evt;
            while (it.hasNext()) {
                evt = it.next();
                error = evt.getErrorMessage();
                VariableBinding[] vbs = evt.getColumns();
                Integer ifIndex = vbs[0].getVariable().toInt();

                if (intList.contains(ifIndex)) {
                    reply.putPerformance(ifIndex, "IfInOctets",
                            vbs[1].getVariable().toLong());
                    reply.putPerformance(ifIndex, "IfInUcastPkts",
                            vbs[2].getVariable().toLong());
                    reply.putPerformance(ifIndex, "IfInNucastPkts",
                            vbs[3].getVariable().toLong());
                    reply.putPerformance(ifIndex, "IfInErrors",
                            vbs[4].getVariable().toLong());

                    reply.putPerformance(ifIndex, "IfOutOctets",
                            vbs[5].getVariable().toLong());
                    reply.putPerformance(ifIndex, "IfOutUcastPkts",
                            vbs[6].getVariable().toLong());
                    reply.putPerformance(ifIndex, "IfOutNucastPkts",
                            vbs[7].getVariable().toLong());
                    reply.putPerformance(ifIndex, "IfOutErrors",
                            vbs[8].getVariable().toLong());
                }
            }

            reply.setStatus(Status.OK);
            reply.setReply("IfPerTableTest success fetch for: " + ifSelection);
            return reply;
        } catch (Exception | Error e) {
            CheckNetworkInterfaces.LOGGER.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Error: " + error);
            return reply;
        }
    }
}
