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
public class CheckLinuxMemory implements CheckInterface {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(CheckLinuxMemory.class);
    private static final String MEM_TOTAL_SWAP = "1.3.6.1.4.1.2021.4.3.0";
    private static final String MEM_AVAIL_SWAP = "1.3.6.1.4.1.2021.4.4.0";
    private static final String MEM_TOTAL_REAL = "1.3.6.1.4.1.2021.4.5.0";
    private static final String MEM_AVAIL_REAL = "1.3.6.1.4.1.2021.4.6.0";
    private static final String MEM_SHARED = "1.3.6.1.4.1.2021.4.13.0";
    private static final String MEM_CACHED = "1.3.6.1.4.1.2021.4.15.0";

    private final PDU pdu;

    public CheckLinuxMemory() {
        this.pdu = new PDU();
        this.pdu.add(new VariableBinding(new OID(MEM_TOTAL_SWAP)));
        this.pdu.add(new VariableBinding(new OID(MEM_AVAIL_SWAP)));
        this.pdu.add(new VariableBinding(new OID(MEM_TOTAL_REAL)));
        this.pdu.add(new VariableBinding(new OID(MEM_AVAIL_REAL)));
        this.pdu.add(new VariableBinding(new OID(MEM_SHARED)));
        this.pdu.add(new VariableBinding(new OID(MEM_CACHED)));

    }

    @Override
    public Reply execute(Query query) {
        Reply reply = new Reply();
        String error = "undefined";

        // TODO use performance group state
        float warningThreshold;
        float criticalThreshold;

        try {
            warningThreshold = query.get("total_mem_warning_threshold").asFloat();
            criticalThreshold = query.get("total_mem_critical_threshold").asFloat();
        } catch (Exception | Error e) {
            CheckLinuxMemory.LOGGER.error(e.getMessage(), e);
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

            int memTotalSwap = respBindings.get(0).getVariable().toInt();
            int memAvailSwap = respBindings.get(1).getVariable().toInt();
            int memTotalReal = respBindings.get(2).getVariable().toInt();
            int memAvailReal = respBindings.get(3).getVariable().toInt();
            int memShared = respBindings.get(4).getVariable().toInt();
            int memCached = respBindings.get(5).getVariable().toInt();

            int memUsedReal = memTotalReal - memAvailReal - memCached;
            int memPercentReal = memUsedReal / (memTotalReal / 100);

            Status status;
            if (memPercentReal > criticalThreshold) {
                status = Status.CRITICAL;
            } else if (memPercentReal > warningThreshold) {
                status = Status.WARNING;
            } else {
                status = Status.OK;
            }

            reply.putPerformance("memTotalSwap", memTotalSwap);
            reply.putPerformance("memAvailSwap", memAvailSwap);
            reply.putPerformance("memTotalReal", memTotalReal);
            reply.putPerformance("memAvailReal", memAvailReal);
            reply.putPerformance("memShared", memShared);
            reply.putPerformance("memCached", memCached);
            reply.setStatus(status);
            reply.setReply("CheckLinuxMemory " + status
                    + " used: " + memPercentReal + "%");
            return reply;
        } catch (Exception | Error e) {
            CheckLinuxMemory.LOGGER.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Error: " + error);
            return reply;
        }
    }
}
