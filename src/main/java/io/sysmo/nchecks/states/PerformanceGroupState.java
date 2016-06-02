package io.sysmo.nchecks.states;

import io.sysmo.nchecks.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private HashMap<Integer, Long> olderData;
    private HashMap<Integer, Long> newerData;
    static Logger logger = LoggerFactory.getLogger(PerformanceGroupState.class);

    public PerformanceGroupState() {
        this.time      = null;
        this.pduType   = PDU.GETNEXT;
        this.status    = Status.UNKNOWN;
        this.olderData = null;
        this.newerData = new HashMap<>();
    }

    public void put(Integer index, Long value) {
        if (this.newerData == null) {
            this.newerData = new HashMap<>();
        }
        this.newerData.put(index, value);
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

        PerformanceGroupState.logger.debug(
                "Before first call: {} {} {} {} {}", this.time,
                this.olderData, this.newerData, this.status, this.pduType);
        if (this.time == null) {
            // this is the first computeStatusMaps call, nothing to compare
            this.time = new Date();
            this.olderData = this.newerData;
            this.newerData = null;
            return this.status;
        }
        PerformanceGroupState.logger.debug(
                "After first call: {} {} {} {} {}", this.time,
                this.olderData, this.newerData, this.status, this.pduType);

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
        for (Map.Entry<Integer, Long> entry : this.newerData.entrySet()) {
            Integer key = entry.getKey();
            Long upd = entry.getValue();
            Long old = this.olderData.get(key); // nullpointerexception from JRuby only???
            if (old != null) {
                long diff = (upd - old) / minutes;
                if (diff > warning && warning >= 1) {
                    newStatus = Status.WARNING;
                }
                if (diff > critical && critical >= 1) {
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
            this.olderData = this.newerData;
            this.newerData = null;
            this.status = newStatus;
            return this.status;
        }
    }
}