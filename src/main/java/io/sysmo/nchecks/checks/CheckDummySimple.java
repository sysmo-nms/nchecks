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
package io.sysmo.nchecks.checks;

import io.sysmo.nchecks.CheckInterface;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Status;

import java.util.Random;

/**
 * Created by seb on 30/07/16.
 *
 * Lightweight check used to test load of the Sysmo server.
 *
 * Do nothing but return a random return status.
 *
 */

public class CheckDummySimple implements CheckInterface {

    public CheckDummySimple() {}

    public Reply execute(Query query) {

        Random generator = new Random();
        int random = generator.nextInt(5);

        Reply reply = new Reply();
        reply.setReply("Dummy reply");

        switch (random) {
            case 0:  reply.setStatus(Status.OK);
            case 1:  reply.setStatus(Status.WARNING);
            case 2:  reply.setStatus(Status.CRITICAL);
            case 3:  reply.setStatus(Status.ERROR);
            default: reply.setStatus(Status.ERROR);
        }

        return reply;
    }
}
