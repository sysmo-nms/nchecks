package io.sysmo.nchecks.states;

import io.sysmo.nchecks.Status;
import org.snmp4j.PDU;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by seb on 30/05/16.
 *
 * Keep state of list of performances values and determine the status of the
 * probe by adding all performances together and compare to warning and critical
 * values.
 *
 */
public class PerformanceGroupState implements Serializable {
    private int pduType;
    private Status status;
    private Date time;
    private HashMap<Integer, Long> data;
    private HashMap<Integer, Long> update;

    public PerformanceGroupState() {
        this.time = null;
        this.pduType = PDU.GETNEXT;
        this.status = Status.UNKNOWN;
    }

    public void put(Integer index, Long value) {
        if (this.update == null) {
            this.update = new HashMap<>();
        }
        this.update.put(index, value);
    }

    public int getPduType() {
        return this.pduType;
    }

    /**
     * Take two counter entries from different dates, compare to critical
     * and warning values and return the appropriate status.
     *
     * @param warning  the warning threshold
     * @param critical the critical threshold
     * @return the new state
     */
    public Status computeStatusMaps(int warning, int critical) {

        if (this.time == null) {
            // this is the first compute statusMap, nothing to compare
            this.time = new Date();
            this.data = this.update;
            this.update = null;
            return this.status;
        }

        // get the minutes diff from last walk
        Date newDate = new Date();
        Date oldDate = this.time;
        long seconds = (newDate.getTime() - oldDate.getTime()) / 1000;
        long minutes;

        boolean keepWorstState;
        if (seconds < 60) {
            keepWorstState = true;
            // no enough time elapsed return old status
            //keepWorstState = true;
            minutes = 1;
        } else {
            keepWorstState = false;
            minutes = seconds / 60;
        }


        Status newStatus = Status.OK;
        // if one of the key reach threshold value set the new status.
        for (Map.Entry<Integer, Long> entry : this.update.entrySet()) {
            Integer key = entry.getKey();
            Long upd = entry.getValue();
            Long old = this.data.get(key);
            if (old != null) {
                long diff = (upd - old) / minutes;
                if (diff > warning) {
                    newStatus = Status.WARNING;
                }
                if (diff > critical) {
                    newStatus = Status.CRITICAL;
                }
            }
        }

        if (keepWorstState) {
            switch (this.status.compareTo(newStatus)) {
                case 1:
                    // this.status is superior keep it and forget update
                    return this.status;
                default:
                    // this.status is equal or inferior change it but stay
                    // with the old update time and update
                    this.status = newStatus;
                    return this.status;
            }
        } else {
            this.time = newDate;
            this.data = this.update;
            this.update = null;
            this.status = newStatus;
            return this.status;
        }
    }
}