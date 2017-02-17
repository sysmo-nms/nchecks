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

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;

/**
 * The Reply class contain all the values and data related to the execution of a
 * module implementing NCheckInterface (a check module).
 *
 * @see io.sysmo.nchecks.Reply
 */
public class Query {

    final private Map<String, Argument> arguments;
    private String stateId;

    /**
     * Private use only.
     *
     * @see io.sysmo.nchecks.Argument
     * @param args a map of Arguments
     * @param stateId a unique string identifying the probe
     */
    public Query(Map<String, Argument> args, final String stateId) {
        this.stateId = stateId;
        this.arguments = new HashMap<>(args);
    }

    /**
     * Private use only.
     *
     * No sate Query, used by HelperInterface
     *
     * @param args a map of Argument
     */
    public Query(Map<String, Argument> args) {
        this.arguments = new HashMap<>(args);
    }

    /**
     * Retrieve the object stored from previous call.
     *
     * example:
     *
     * ... MyStateClass state = (MyStateClass) reply.getState(); if (state ==
     * null) { // initialize a new state then state = new MySateClass(); } ...
     *
     * @see io.sysmo.nchecks.Reply#setState(Serializable)
     * @return an Object set from Reply.setState or null
     */
    public Object getState() {
        // Lazy getState. Modules not using states, will not use it.
        StateMessage msg = new StateMessage(StateMessage.GET);
        msg.setKey(this.stateId);
        // getState will get the data from the (possibly) distant state server.
        return StateClient.getState(msg);
    }

    /**
     *
     * @return the unique string identifying the probe
     */
    public String getStateId() {
        return this.stateId;
    }

    /**
     * Return the argument identified by key or null if the key does not exist.
     *
     * @param key the flag identifying the argument
     * @return the argument or null
     */
    public Argument get(String key) {
        if (this.arguments.containsKey(key)) {
            return this.arguments.get(key);
        } else {
            return null;
        }
    }
}
