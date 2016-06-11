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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Created by seb on 20/10/15.
 *
 * This class is used by StateClient and StateServer to communicate over TCP.
 */
public class StateMessage implements Serializable {
    private String key;
    private byte[] value;
    private int action;
    public static final int GET = 0;
    public static final int SET = 1;


    /**
     * Build a query of type StateMessage.GET or StateMessage.SET.
     * StateMessage.SET require an object as state.
     * @see StateMessage setObjectState(Object obj)
     * @param action GET or SET
     */
    StateMessage(int action) {
        this.action = action;
    }


    /**
     * Set object state.
     * @param value a serializable object
     */
    public void setObject(Serializable value) {
        if (value == null) return;

        ByteArrayOutputStream outStream = null;
        ObjectOutput objOut = null;
        try {
            outStream = new ByteArrayOutputStream();
            objOut = new ObjectOutputStream(outStream);
            objOut.writeObject(value);
            this.value = outStream.toByteArray();
        } catch (IOException e) {
            this.value = new byte[0];
        } finally {
            if(outStream != null) {
                try {
                    outStream.close();
                } catch (IOException inner) {
                    // ignore
                }
            }
            if (objOut != null) {
                try {
                    objOut.close();
                } catch (IOException inner) {
                    // ignore
                }
            }
        }
    }


    /**
     * Set the key state.
     * @param key the unique key
     */
    public void setKey(String key) {
        this.key = key;
    }


    /**
     * Get the value object as byte array.
     * @return value object as byte array
     */
    public byte[] getObjectBytes() {
        return this.value.clone();
    }


    /**
     * Get the object set in constructor.
     * @return an Object
     */
    public Object getObject() {
        if (this.value.length == 0) return null;

        ByteArrayInputStream inStream = null;
        ObjectInput objIn = null;

        try {
            inStream = new ByteArrayInputStream(this.value);
            objIn = new ObjectInputStream(inStream);
            return objIn.readObject();
        } catch (ClassNotFoundException|IOException e) {
            return null;
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException inner) {
                    // ignore
                }
            }

            if (objIn != null) {
                try {
                    objIn.close();
                } catch (IOException inner) {
                    // ignore
                }
            }
        }
    }


    /**
     * Get the key of this message.
     * @return the key
     */
    public String getKey() {
        return this.key;
    }


    /**
     * Get the action of this message.
     * @return "get" or "set"
     */
    public int getAction() {
        return this.action;
    }


    /**
     * Set the Object value of the message in byte array form.
     * @param value a byte array
     */
    public void setBytes(byte[] value) {
        this.value = value.clone();
    }
}
