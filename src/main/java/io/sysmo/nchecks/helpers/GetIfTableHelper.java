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
package io.sysmo.nchecks.helpers;

import io.sysmo.nchecks.HelperInterface;
import io.sysmo.nchecks.HelperReply;
import io.sysmo.nchecks.HelperSimpleReply;
import io.sysmo.nchecks.snmp.Manager;
import io.sysmo.nchecks.HelperTableReply;
import io.sysmo.nchecks.HelperTableRow;
import io.sysmo.nchecks.Query;

import org.snmp4j.AbstractTarget;
import org.snmp4j.PDU;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Provide a selectable list of interfaces with multiple choices, grouped by
 * ifType.
 */
public class GetIfTableHelper implements HelperInterface {

    private static final String IF_INDEX = "1.3.6.1.2.1.2.2.1.1";
    private static final String IF_DESCR = "1.3.6.1.2.1.2.2.1.2";
    private static final String IF_TYPE = "1.3.6.1.2.1.2.2.1.3";
    private static final String IF_PHYSADDRESS = "1.3.6.1.2.1.2.2.1.6";

    private static final OID[] COLUMNS = new OID[]{
        new OID(IF_INDEX),
        new OID(IF_DESCR),
        new OID(IF_TYPE),
        new OID(IF_PHYSADDRESS)
    };

    private static final Map<String, String> IF_TYPES;

    static {
        IF_TYPES = new HashMap<>();
        IF_TYPES.put("1", "other");
        IF_TYPES.put("2", "regular1822");
        IF_TYPES.put("3", "hdh1822");
        IF_TYPES.put("4", "ddn-x25");
        IF_TYPES.put("5", "rfc877-x25");
        IF_TYPES.put("6", "ethernet-csmacd");
        IF_TYPES.put("7", "iso88023-csmacd");
        IF_TYPES.put("8", "iso88024-tokenBus");
        IF_TYPES.put("9", "iso88025-tokenRing");
        IF_TYPES.put("10", "iso88026-man");
        IF_TYPES.put("11", "starLan");
        IF_TYPES.put("12", "proteon-10Mbit");
        IF_TYPES.put("13", "proteon-80Mbit");
        IF_TYPES.put("14", "hyperchannel");
        IF_TYPES.put("15", "fddi");
        IF_TYPES.put("16", "lapb");
        IF_TYPES.put("17", "sdlc");
        IF_TYPES.put("18", "ds1");
        IF_TYPES.put("19", "e1");
        IF_TYPES.put("20", "basicISDN");
        IF_TYPES.put("21", "primaryISDN");
        IF_TYPES.put("22", "propPointToPointSerial");
        IF_TYPES.put("23", "ppp");
        IF_TYPES.put("24", "softwareLoopback");
        IF_TYPES.put("25", "eon");
        IF_TYPES.put("26", "ethernet-3Mbit");
        IF_TYPES.put("27", "nsip");
        IF_TYPES.put("28", "slip");
        IF_TYPES.put("29", "ultra");
        IF_TYPES.put("30", "ds3");
        IF_TYPES.put("31", "sip");
        IF_TYPES.put("32", "frame-relay");
    }

    public GetIfTableHelper() {
    }

    @Override
    public HelperReply callHelper(Query query) {
        try {
            AbstractTarget target = Manager.getTarget(query);
            TableUtils tableWalker = Manager.getTableUtils(PDU.GETNEXT);

            List<TableEvent> snmpReply = tableWalker.getTable(
                    target, COLUMNS, null, null);

            Iterator<TableEvent> it = snmpReply.iterator();
            TableEvent evt;

            HelperTableReply table = new HelperTableReply();
            int rowCount = 0;
            while (it.hasNext()) {
                rowCount += 1;
                evt = it.next();
                VariableBinding[] vbs = evt.getColumns();
                HelperTableRow row = new HelperTableRow();
                row.addItem("ifIndex", vbs[0].getVariable().toString());
                row.addItem("ifDescr", vbs[1].getVariable().toString());
                row.addItem("ifType", getType(vbs[2].getVariable().toString()));
                row.addItem("ifPhysAddress", vbs[3].getVariable().toString());
                table.addRow(row);
            }

            if (rowCount == 0) {
                table.setId("SelectNetworkInterfaces");
                table.setStatus(HelperReply.FAILURE);
                table.setMessage(
                        "Unable to get an interfaces list from the host."
                        + "The cause may be : \n"
                        + "- a partial implementation of the SNMP protocol, \n"
                        + "- an access control denied the Sysmo SNMP manager, \n");
            } else {
                table.setId("SelectNetworkInterfaces");
                table.setStatus(HelperReply.SUCCESS);
                table.setMessage("Please select interfaces you want to monitor:");
                table.setTreeRoot("ifType");
                table.setSelectColumn("ifIndex");
                table.setSelectType(HelperTableReply.SELECT_MULTIPLE);
                table.setListSeparator(",");
            }

            return table;

        } catch (Exception | Error e) {
            HelperSimpleReply simple = new HelperSimpleReply();
            simple.setId("SelectNetworkInterfaces");
            simple.setStatus(HelperReply.FAILURE);
            simple.setMessage("Error, the SNMP query has failed.");
            return simple;
        }
    }

    private static String getType(String type) {
        String val = IF_TYPES.get(type);
        if (val == null) {
            return "unknown(" + type + ")";
        }
        return val;
    }
}
