/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.sysmo.nchecks;

import java.util.Map;

/**
 * Like io.sysmo.nchecks.Query, but do not call a StateServer. Used for
 * development purpose.
 *
 * @author Sébastien Serre <sserre at msha.fr>
 */
public class SimpleQuery extends Query {

    public SimpleQuery(Map<String, Argument> args, final String stateId) {
        super(args, stateId);
    }

    public SimpleQuery(Map<String, Argument> args) {
        super(args);
    }

    @Override
    public Object getState() {
        return null;
    }

}
