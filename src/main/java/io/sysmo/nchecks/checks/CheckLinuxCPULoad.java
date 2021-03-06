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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.AbstractTarget;
import org.snmp4j.PDU;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;

import java.util.List;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;

/**
 * Definition of the check is in the file CheckIfTraffic.xml
 */
public class CheckLinuxCPULoad implements CheckInterface {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(CheckLinuxCPULoad.class);
    private static final String CPU_LOAD_1MN = "1.3.6.1.4.1.2021.10.1.3.1";
    private static final String CPU_LOAD_5MN = "1.3.6.1.4.1.2021.10.1.3.2";
    private static final String CPU_LOAD_15MN = "1.3.6.1.4.1.2021.10.1.3.3";

    private final PDU pdu;

    public CheckLinuxCPULoad() {
        this.pdu = new PDU();
        this.pdu.add(new VariableBinding(new OID(CPU_LOAD_1MN)));
        this.pdu.add(new VariableBinding(new OID(CPU_LOAD_5MN)));
        this.pdu.add(new VariableBinding(new OID(CPU_LOAD_15MN)));

    }

    @Override
    public Reply execute(Query query) {
        Reply reply = new Reply();
        String error = "undefined";

        // TODO use performance group state
        float warningThreshold;
        float criticalThreshold;

        try {
            warningThreshold = query.get("warning_threshold").asFloat();
            criticalThreshold = query.get("critical_threshold").asFloat();
        } catch (Exception | Error e) {
            CheckLinuxCPULoad.LOGGER.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Missing or wrong argument: " + e);
            return reply;
        }

        try {

            AbstractTarget target = Manager.getTarget(query);
            Snmp snmpMan = Manager.getManager();
            ResponseEvent resp = snmpMan.get(this.pdu, target);

            PDU respPDU = resp.getResponse();
            List<? extends VariableBinding> respBindings = respPDU.getVariableBindings();

            float one = Float.parseFloat(respBindings.get(0).getVariable().toString());
            float five = Float.parseFloat(respBindings.get(1).getVariable().toString());
            float fifteen = Float.parseFloat(respBindings.get(2).getVariable().toString());

            Status status;
            if (fifteen > criticalThreshold) {
                status = Status.CRITICAL;
            } else if (fifteen > warningThreshold) {
                status = Status.WARNING;
            } else {
                status = Status.OK;
            }

            reply.putPerformance("cpuLoad1mn", (long) (one * 1000));
            reply.putPerformance("cpuLoad5mn", (long) (five * 1000));
            reply.putPerformance("cpuLoad15mn", (long) (fifteen * 1000));
            reply.setStatus(status);
            reply.setReply("CheckLinuxCPULoad " + status);
            return reply;
        } catch (Exception | Error e) {
            CheckLinuxCPULoad.LOGGER.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Error: " + error);
            return reply;
        }
    }
}
