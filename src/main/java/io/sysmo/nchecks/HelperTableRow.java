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

public class HelperTableRow
{
    private ArrayList<HelperTableItem> items;

    public HelperTableRow() {
        items = new ArrayList<>();
    }

    /*
    * Add the string value "value" to the column "column".
    */
    public void addItem(String column, String value) {
        items.add(new HelperTableItem(column, value));
    }

    /*
    * Return the list of HelperTableItem for the row.
    */
    public List<HelperTableItem> getItems() {
        return items;
    }
}
