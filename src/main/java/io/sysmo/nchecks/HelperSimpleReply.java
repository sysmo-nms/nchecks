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

import java.io.CharArrayWriter;
import javax.json.Json;
import javax.json.JsonWriter;
import javax.json.JsonObjectBuilder;

/*
* This class build a simple message for client view. It will pop up
* with an icon corresponding to the status, and the message defined.
* It will fill the flag as defined in the xml check definition file
* to "value". See tutorials on Nchecks Helpers xml for more informations
*/

public class HelperSimpleReply implements HelperReply
{

    private String  messageId   = "";
    private String  message     = "";
    private String  value       = "";
    private String  status      = HelperReply.SUCCESS;

    public HelperSimpleReply() {}

    /*
    * Set the identifier of the reply
    */
    public void setId(String val)  { this.messageId = val; }

    /* set the status of the reply. Must be HelperReply.SUCCESS or
    * HelperReply.FAILURE.
    */
    public void setStatus(String val) { this.status = val; }

    /*
    * Set the message string show to the user.
    */
    public void setMessage(String val) { this.message = val; }

    /*
    * Set the value for the target flag.
    */
    public void setValue(String val) { this.value = val; }

    /*
     * Build a json representation of the message.
     */
    public char[] toCharArray()
    {
        CharArrayWriter     buffer          = new CharArrayWriter();
        JsonWriter          jsonWriter      = Json.createWriter(buffer);
        JsonObjectBuilder   objectbuilder   = Json.createObjectBuilder();

        objectbuilder.add("status",     this.status);
        objectbuilder.add("id",         this.messageId);
        objectbuilder.add("message",    this.message);
        objectbuilder.add("value",      this.value);
        
        jsonWriter.writeObject(objectbuilder.build());
        return buffer.toCharArray();
    }
}
