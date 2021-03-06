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

import java.util.List;
import java.util.ArrayList;

import java.io.CharArrayWriter;
import javax.json.Json;
import javax.json.JsonWriter;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;

/**
 * This class build a simple message for client view. It will pop up
 * with an icon corresponding to the status, and the message defined.
 * It will fill the flag as defined in the xml check definition file
 * to "value". See tutorials for more informations
 */

public class HelperTableReply implements HelperReply
{

    public static final String SELECT_SINGLE   = "single";
    public static final String SELECT_MULTIPLE = "multiple";
    private String messageId = "";
    private String message   = "";
    private String status    = HelperReply.SUCCESS;
    private String treeRoot  = "";
    private String select    = "";
    private String selectionType = "";
    private String listSeparator = "";

    private ArrayList<HelperTableRow> rows;
    public HelperTableReply() {
        rows = new ArrayList<>();
    }


    /*
    *  Add a HelperTableRow to the table.
    */
    public void addRow(HelperTableRow row) {
        this.rows.add(row);
    }



    /*
    *  Set the reply id.
    */
    public void setId(String val)  { this.messageId = val; }

    /*
    *  Set the status
    * (HelperReply.SUCCESS | HelperReply.FAILURE)
    */
    public void setStatus(String val) { this.status = val; }

    /*
    *  Set the message string shown on top of the table.
    */
    public void setMessage(String val) { this.message = val; }

    /*
     * Set the treeroot
     */
    public void setTreeRoot(String val) { this.treeRoot = val; }

    /*
     * Set the selectCol
     */
    public void setSelectColumn(String val) { this.select = val; }

    /*
     * Set the selectType
    * (HelperReply.SINGLE | HelperReply.MULTIPLE)
     */
    public void setSelectType(String val) { this.selectionType = val; }


    /*
     * Set the list separator
     */
    public void setListSeparator(String val) { this.listSeparator = val; }


    public char[] toCharArray()
    {
        CharArrayWriter     buffer          = new CharArrayWriter();
        JsonBuilderFactory  factory         = Json.createBuilderFactory(null);
        JsonWriter          jsonWriter      = Json.createWriter(buffer);
        JsonObjectBuilder   objectBuilder   = factory.createObjectBuilder();
        JsonArrayBuilder    arrayBuilder    = factory.createArrayBuilder();

        for (HelperTableRow row : this.rows) {
            JsonObjectBuilder rowObj = factory.createObjectBuilder();
            List<HelperTableItem> items = row.getItems();

            for (HelperTableItem item : items) {
                String key = item.getColumn();
                String value = item.getValue();
                rowObj.add(key, value);
            }
            arrayBuilder.add(rowObj);
        }

        objectBuilder.add("type",          "table");
        objectBuilder.add("treeRoot",      treeRoot);
        objectBuilder.add("select",        select);
        objectBuilder.add("selectionType", selectionType);
        objectBuilder.add("listSeparator", listSeparator);

        objectBuilder.add("status",  status);
        objectBuilder.add("message", message);
        objectBuilder.add("id",      messageId);
        objectBuilder.add("rows",    arrayBuilder);
        jsonWriter.writeObject(objectBuilder.build());
        return buffer.toCharArray();
    }
}
