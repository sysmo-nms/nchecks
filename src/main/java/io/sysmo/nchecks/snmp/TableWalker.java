package io.sysmo.nchecks.snmp;

import io.sysmo.nchecks.Query;

import org.snmp4j.AbstractTarget;
import org.snmp4j.PDU;
import org.snmp4j.smi.OID;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by seb on 30/05/16.
 */
public class TableWalker
{
    private List<OID>     columns;
    private List<Integer> indexes;

    public TableWalker() {
        this.columns = new ArrayList<>();
        this.indexes = new ArrayList<>();
    }

    public void addColumn(String oidString) {
        this.columns.add(new OID(oidString));
    }

    public void addIndex(String indexString) {
        this.indexes.add(Integer.parseInt(indexString));
    }

    public List<Integer> getIndexes() {
        return this.indexes;
    }

    public List<TableEvent> walk(Query query) throws Exception {

        OID[] columnArray = new OID[this.columns.size()];
        columns.toArray(columnArray);
        Integer[] indexesArray = new Integer[this.indexes.size()];
        indexes.toArray(indexesArray);

        // sort indexes
        Arrays.sort(indexesArray);

        // build upper and lower bound indexes
        Integer lower = indexesArray[0] - 1;
        Integer upper = indexesArray[indexesArray.length - 1];
        OID lowerBoundIndex = new OID(lower.toString());
        OID upperBoundIndex = new OID(upper.toString());

        AbstractTarget target = Manager.getTarget(query);

        // TODO try PDU.GETBULK then PDU.GETNEXT to degrade....
        // TODO keep degrade state in reply.setState(v)
        TableUtils tableWalker = Manager.getTableUtils(PDU.GETNEXT);
        List<TableEvent> snmpReply = tableWalker.getTable(
                target, columnArray,
                lowerBoundIndex, upperBoundIndex);

        return snmpReply;
    }
}