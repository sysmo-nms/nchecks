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
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Query;
import io.sysmo.nchecks.NChecksJRuby;
import io.sysmo.nchecks.Status;
import org.jruby.embed.ScriptingContainer;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class CheckViaJRuby implements CheckInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckViaJRuby.class);

    @Override
    public Reply execute(Query query) {

        // try to get the ScriptingContainer of the script
        String rbScript = "undefined";
        ScriptingContainer script;
        try {

            rbScript = query.get("check_id").asString();
            script = NChecksJRuby.getScript(rbScript);

        } catch (Exception e) {

            CheckViaJRuby.LOGGER.error(e.getMessage(), e);
            return CheckViaJRuby.handleException(
                    "Script not found: " + rbScript, e);

        }

        // try to call it
        Reply rep;
        try {

            rep = script.callMethod(null, "check", query, Reply.class);
            return rep;

        } catch (Exception e) {

            CheckViaJRuby.LOGGER.error(e.getMessage(), e);
            return CheckViaJRuby.handleException(
                    "Script execution failure: " + rbScript, e);

        } catch (Error e) {

            CheckViaJRuby.LOGGER.error("JRuby exec error");
            return CheckViaJRuby.handleError("Script execution failure.");

        }
    }

    private static Reply handleException(String txt, Exception e) {

        CheckViaJRuby.LOGGER.error(e.getMessage(), e);

        Reply reply = new Reply();
        reply.setStatus(Status.ERROR);
        reply.setReply("CheckViaJRuby ERROR: " + txt + e.getMessage());

        return reply;

    }

    private static Reply handleError(String txt) {

        Reply reply = new Reply();
        reply.setStatus(Status.ERROR);
        reply.setReply("CheckViaJRuby ERROR: " + txt);

        return reply;

    }
}
