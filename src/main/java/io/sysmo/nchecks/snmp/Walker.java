package io.sysmo.nchecks.snmp;

import io.sysmo.nchecks.CheckInterface;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Status;
import io.sysmo.nchecks.states.PerformanceGroupState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.AbstractTarget;
import org.snmp4j.PDU;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by seb on 30/05/16.
 */
public class Walker implements CheckInterface
{
    static Logger logger = LoggerFactory.getLogger(Walker.class);
    private static String IF_INDEX      = "1.3.6.1.2.1.2.2.1.1";
    private static String IF_IN_OCTETS  = "1.3.6.1.2.1.2.2.1.10";
    private static String IF_OUT_OCTETS = "1.3.6.1.2.1.2.2.1.16";

    private static OID[] columns = new OID[]{
            new OID(IF_INDEX),
            new OID(IF_IN_OCTETS),
            new OID(IF_OUT_OCTETS)
    };

    public Walker() {}

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
            Walker.logger.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Missing or wrong argument: " + e);
            return reply;
        }

        PerformanceGroupState state = null;
        // Avoid calling query.getState() if not required.
        if (warningThreshold != 0 || criticalThreshold != 0) {
           state = (PerformanceGroupState) query.getState();
            if (state == null) {
                state = new PerformanceGroupState();
            }
        }

        HashMap<Integer, Long> newStatusMap = new HashMap<>();
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
            TableUtils tableWalker = Manager.getTableUtils(PDU.GETNEXT);
            List<TableEvent> snmpReply = tableWalker.getTable(
                    target, Walker.columns,
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
                    Long octetsIn = vbs[1].getVariable().toLong();
                    Long octetsOut = vbs[2].getVariable().toLong();
                    reply.putPerformance(ifIndex, "IfInOctets", octetsIn);
                    reply.putPerformance(ifIndex, "IfOutOctets", octetsOut);

                    newStatusMap.put(ifIndex, octetsIn);
                }
            }

            String replyMsg;
            Status newStatus;
            // Avoid calling reply.setState() if not required.
            if (state != null) {
                newStatus = state.computeStatusMaps(
                        newStatusMap, warningThreshold, criticalThreshold);

                if (newStatus.equals(Status.OK)) {
                    replyMsg = "Walker OK";
                } else if (newStatus.equals(Status.UNKNOWN)) {
                    replyMsg = "Walker UNKNOWN. No enough data to set sensible status.";
                } else if (newStatus.equals(Status.WARNING)) {
                    replyMsg = "Walker have exceeded WARNING threshold!";
                } else if (newStatus.equals(Status.CRITICAL)) {
                    replyMsg = "Walker have exceeded CRITICAL threshold!";
                } else {
                    replyMsg = "";
                }
                reply.setState(state);
            } else {
                newStatus = Status.OK;
                replyMsg = "Walker OK";
            }

            reply.setStatus(newStatus);
            reply.setReply(replyMsg);
            return reply;
        } catch (Exception|Error e) {
            Walker.logger.error(e.getMessage(), e);
            reply.setStatus(Status.ERROR);
            reply.setReply("Error: " + error);
            return reply;
        }
    }
}