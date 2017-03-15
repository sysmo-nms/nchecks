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
package io.sysmo.nchecks;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangString;
import com.ericsson.otp.erlang.OtpErlangTuple;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * The Reply class contain all the values and data related to the execution of a
 * module implementing NCheckInterface (a check module).
 *
 * @see io.sysmo.nchecks.Query
 */
public class Reply {

    private static final OtpErlangAtom ATOM_NCHECKS_REPLY = new OtpErlangAtom("nchecks_reply");
    private String replyMsg;
    private Status status;
    private final long timestamp;
    private final Map<String, PerformanceGroup> perfValues;
    private int statusCode;
    private Serializable state;

    public Reply() {
        this.statusCode = 0;
        this.replyMsg = "";
        this.status = Status.UNKNOWN;
        this.timestamp = System.currentTimeMillis() / 1000;
        this.perfValues = new HashMap<>();
        this.state = null;
    }

    @Override
    public String toString() {
        String perfs = "";

        for (Entry<String, PerformanceGroup> e : this.perfValues.entrySet()) {
            perfs += "\n\t" + e.getKey() + ":\t" + e.getValue().toString();
        }

        return "\n ReplyMsg: \t" + this.replyMsg
                + "\n Status: \t" + this.status
                + "\n Timestamp: \t" + this.timestamp
                + "\n StatusCode: \t" + this.statusCode
                + "\n Performances:" + perfs;

    }

    /**
     * The status code is used to determine if the event should be logged to the
     * event database when two subsequent checks have the same status
     *
     * The default status code is 0
     *
     * Here are the rules concerning "status" ans "statusCode": - All status
     * move (ie: from OK to ERROR...) are logged. - If two (or more) consecutive
     * returns have the same status (CRITICAL for example) and the same
     * statusCode, only the first event will be logged. - If two (or more)
     * consecutive returns have the same status (CRITICAL for example) and a
     * different statusCode, every event where a statusCode have changed will be
     * logged.
     *
     * @param statusCode integer representing the status code
     */
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Set the status code for this reply (see setStatusCode). The default value
     * is 0.
     *
     * @see io.sysmo.nchecks.Reply#setStatusCode(int)
     * @return the actual status code.
     */
    public int getStatusCode() {
        return this.statusCode;
    }

    /**
     * Set the reply string of this check. It should be a human readable string.
     *
     * @param str the string representing the reply.
     */
    public void setReply(String str) {
        replyMsg = str;
    }

    /**
     * Get the reply string.
     *
     * @see io.sysmo.nchecks.Reply#setReply(String)
     * @return the reply string
     */
    public String getReply() {
        return replyMsg;
    }

    /**
     * Store a state for this probe for later calls. The object will be
     * available in the next queries with Query#getState().
     *
     * @see io.sysmo.nchecks.Query#getState()
     *
     * @param value the Object to be stored
     * @throws Exception an error
     */
    public void setState(Serializable value) throws Exception {
        this.state = value;
    }

    /**
     * For internal use only. This function is called by NChecks with an unique
     * key for you.
     *
     * @see io.sysmo.nchecks.Query#getState()
     * @param key unique key identifying the probe
     */
    public void pushState(String key) {
        if (this.state != null) {
            StateMessage msg = new StateMessage(StateMessage.SET);
            msg.setKey(key);
            msg.setObject(this.state);
            StateClient.setState(msg);
        }
    }

    /**
     * Set the return status.
     *
     * @see io.sysmo.nchecks.Reply#setStatusCode(int)
     * @param status A return status (Status.{ERROR|OK|WARNING|CRITICAL})
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Get the return status.
     *
     * @return a status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Store a performance value. These performances values will be interpreted
     * by rrd as defined in your module xml definition file. Assume there is
     * only one rrd file (with multiple databases) to update.
     *
     * @param key the rrd database name
     * @param value the value to store
     */
    public void putPerformance(String key, long value) {
        putPerformance("simple", key, value);
    }

    /**
     * Store a performance value. These performances values will be interpreted
     * by rrd as defined in your module xml definition file. The "group" String
     * is used to identify one of the rrd files to update. Assume a setup with
     * multiple rrd files.
     *
     * @param group the performance group key
     * @param key the key
     * @param value the value
     */
    public void putPerformance(String group, String key, long value) {
        PerformanceGroup groupObj = getPerformanceGroup(group);
        groupObj.putPerformance(key, value);
    }

    /**
     * Will call putPerformance(Integer.toString(group),key...)
     *
     * @param group the performance group key
     * @param key the key
     * @param value the value
     */
    public void putPerformance(int group, String key, long value) {
        this.putPerformance(Integer.toString(group), key, value);
    }

    /**
     * Internal use. Return the PerformanceGroup for key groupKey. Create it if
     * it does not exist.
     *
     * @param groupKey the performance group key
     * @return the performance group
     */
    private PerformanceGroup getPerformanceGroup(String groupKey) {
        PerformanceGroup group = perfValues.get(groupKey);
        if (group == null) {
            group = new PerformanceGroup();
            perfValues.put(groupKey, group);
            return group;
        } else {
            return group;
        }
    }

    /**
     * Internal use only for ErlangNodeNChecks. Return the erlang representation
     * of the Reply.
     *
     * @return reply as
     */
    public OtpErlangTuple asTuple() {
        OtpErlangObject[] perfValuesObj = new OtpErlangObject[this.perfValues.size()];
        int i = 0;
        for (Map.Entry<String, PerformanceGroup> entry : perfValues.entrySet()) {
            OtpErlangList value = entry.getValue().asList();
            OtpErlangObject[] objEntry = new OtpErlangObject[2];
            objEntry[0] = new OtpErlangString(entry.getKey());
            objEntry[1] = value;
            perfValuesObj[i] = new OtpErlangTuple(objEntry);
            i++;
        }
        OtpErlangList perfValueList = new OtpErlangList(perfValuesObj);

        OtpErlangObject[] replyRecord = new OtpErlangObject[6];
        replyRecord[0] = Reply.ATOM_NCHECKS_REPLY;
        replyRecord[1] = new OtpErlangString(this.status.toString());
        replyRecord[2] = new OtpErlangLong(this.statusCode);
        replyRecord[3] = perfValueList;
        replyRecord[4] = new OtpErlangString(this.replyMsg);
        replyRecord[5] = new OtpErlangLong(this.timestamp);

        return new OtpErlangTuple(replyRecord);
    }

    // utility classes
    static class PerformanceGroup {

        private final Map<String, Long> perfValues;

        public PerformanceGroup() {
            perfValues = new HashMap<>();
        }

        public void putPerformance(String key, long value) {
            perfValues.put(key, value);
        }

        public OtpErlangList asList() {
            OtpErlangObject[] perfValuesObj = new OtpErlangObject[perfValues.size()];
            int i = 0;
            for (Map.Entry<String, Long> entry : perfValues.entrySet()) {
                OtpErlangObject[] objEntry = new OtpErlangObject[2];
                objEntry[0] = new OtpErlangString(entry.getKey());
                objEntry[1] = new OtpErlangLong(entry.getValue().longValue());
                perfValuesObj[i] = new OtpErlangTuple(objEntry);
                i++;
            }
            return new OtpErlangList(perfValuesObj);
        }

        @Override
        public String toString() {
            String perfs = "";
            Iterator it = this.perfValues.entrySet().iterator();

            while (it.hasNext()) {
                Entry pair = (Entry) it.next();
                perfs += "\n\t\t" + pair.getKey() + ":\t" + pair.getValue().toString();
            }
            return perfs;
        }
    }
}
