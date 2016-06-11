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

/**
 *
 * Created by seb on 23/10/15.
 *
 * Comparable and printable status
 *
 * Status weight:
 * <ul>
 *     <li>OK 10</li>
 *     <li>UNKNOWN 20</li>
 *     <li>WARNING 30</li>
 *     <li>CRITICAL 40</li>
 *     <li>ERROR 50</li>
 * </ul>
 *
 */
public class Status implements Comparable<Status>, Serializable {
    public static final Status OK = new Status("OK", 10);
    public static final Status UNKNOWN = new Status("UNKNOWN",20);
    public static final Status WARNING = new Status("WARNING",30);
    public static final Status CRITICAL = new Status("CRITICAL",40);
    public static final Status ERROR = new Status("ERROR",50);

    private String str;
    private int weight;

    public static Status fromString(String str) {
        switch (str) {
            case "UNKNOWN": return Status.UNKNOWN;
            case "OK": return Status.OK;
            case "ERROR": return Status.ERROR;
            case "WARNING": return Status.WARNING;
            case "CRITICAL": return Status.CRITICAL;
            default: return null;
        }
    }

    private Status(String str, int weight) {
        this.str = str;
        this.weight = weight;
    }

    public int compareTo(Status status) {
        int other = status.getWeight();
        if (this.weight == other) {
            return 0;
        } else if (this.weight < other) {
            return -1;
        } else {
            return 1;
        }
    }

    public boolean equals(Status status) {
        return (status.getWeight() == this.getWeight());
    }

    public int getWeight() {
        return this.weight;
    }

    @Override
    public String toString() {
        return this.str;
    }
}
