/*
 * Sysmo NMS Network Management and Monitoring solution (http://www.sysmo.io)
 *
 * Copyright (c) 2012-2015 Sebastien Serre <ssbx@sysmo.io>
 *
 * This file is part of Sysmo NMS.
 *
 * Sysmo NMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sysmo NMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Sysmo.  If not, see <http://www.gnu.org/licenses/>.
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