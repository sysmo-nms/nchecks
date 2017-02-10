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

public class Argument {

    private String argument;

    public Argument() {
        this.argument = "";
    }

    /**
     * Internal use only.
     *
     * @param val the string value of the argument
     */
    public void set(String val) {
        this.argument = val;
    }

    /**
     * Return the integer representation of the argument.
     *
     * @return integer representation of the argument
     * @throws NumberFormatException bad number
     */
    public int asInteger() throws NumberFormatException {
        return Integer.parseInt(this.argument);
    }

    /**
     * Return the string value of argument
     *
     * @return the original argument string
     */
    public String asString() {
        return this.argument;
    }
}
