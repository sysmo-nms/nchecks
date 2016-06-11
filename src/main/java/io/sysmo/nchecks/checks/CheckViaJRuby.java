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

public class CheckViaJRuby implements CheckInterface
{
    private static Logger logger = LoggerFactory.getLogger(CheckViaJRuby.class);

    public Reply execute(Query query)
    {
        String rbScript = "undefined";
        String script;
        try {
            rbScript = query.get("check_id").asString();
            script = NChecksJRuby.getScript(rbScript);
        } catch (Exception e) {
            CheckViaJRuby.logger.error(e.getMessage(), e);
            return CheckViaJRuby.handleException(
                    "Script not found: " + rbScript, e);
        }

        Reply rep;
        try {
            ScriptingContainer container = new ScriptingContainer();
            /* TODO better: https://github.com/jruby/jruby/wiki/RedBridgeExamples#Parse_Once_Eval_Many_Times
               EmbedEvalUnit unit = container.parse(PathType.CLASSPATH, script);
               then on each script call unit.run().

               See: https://github.com/jruby/jruby/wiki/RedBridge#Context_Instance_Type
               for concurrency.*/

            Object receiver = container.runScriptlet(script);
            rep = container.callMethod(receiver,"check",query,Reply.class);
        } catch(Exception e) {
            CheckViaJRuby.logger.error(e.getMessage(), e);
            return CheckViaJRuby.handleException(
                    "Script execution failure: " + rbScript, e);
        } catch (Error e) {
            CheckViaJRuby.logger.error("JRuby exec error");
            return CheckViaJRuby.handleError("Script execution failure.");
        }
        return rep;
    }

    private static Reply handleException(String txt, Exception e) {
        String msg = e.getMessage();
        CheckViaJRuby.logger.error(e.getMessage(), e);
        Reply reply = new Reply();
        reply.setStatus(Status.ERROR);
        reply.setReply("CheckViaJRuby ERROR: " + txt + msg);
        return reply;
    }

    private static Reply handleError(String txt) {
        Reply reply = new Reply();
        reply.setStatus(Status.ERROR);
        reply.setReply("CheckViaJRuby ERROR: " + txt);
        return reply;
    }
}
