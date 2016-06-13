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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Properties;

import org.jruby.embed.PathType;
import org.jruby.embed.ScriptingContainer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class NChecksJRuby {
    private JRubyCache cache;
    private static NChecksJRuby instance = null;

    public static void startJRubyCache(String scriptPath, String etcDir) {
        if (NChecksJRuby.instance == null) {
            NChecksJRuby.instance = new NChecksJRuby(scriptPath, etcDir);
        }
    }

    private NChecksJRuby(String scriptPath, String etcDir) {
        Logger logger = LoggerFactory.getLogger(NChecksJRuby.class);
        String nchecksConf = Paths.get(etcDir, "nchecks.properties").toString();
        Boolean should_cache;
        InputStream input = null;
        try {
            Properties props = new Properties();
            input = new FileInputStream(nchecksConf);
            props.load(input);
            should_cache = props.getProperty("cache_ruby_files").equals("true");
        } catch (IOException e) {
            // no config file found
            should_cache = true;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        if (should_cache) {
            this.cache = new StandardCache(scriptPath);
        } else {
            this.cache = new NoCache(scriptPath);
        }
        logger.info("JRuby script path: " + scriptPath);
        logger.info("JRuby cache files: " + should_cache.toString());
    }

    public static ScriptingContainer getScript(String identifier) throws Exception {
        return NChecksJRuby.instance.cache.getScript(identifier);
    }

    interface JRubyCache {
        ScriptingContainer getScript(String identifier) throws Exception;
    }

    static class StandardCache implements JRubyCache
    {
        private String scriptPath;
        private HashMap<String,ScriptingContainer> scriptMap;
        private final Object lock = new Object();

        StandardCache(String scriptPath) {
            this.scriptPath = scriptPath;
            this.scriptMap = new HashMap<>();
        }

        public ScriptingContainer getScript(String identifier) throws Exception {
            synchronized (this.lock) {

                ScriptingContainer container = this.scriptMap.get(identifier);

                if (container == null) {
                    String script = identifier + ".rb";
                    container = new ScriptingContainer();
                    container.runScriptlet(PathType.ABSOLUTE,
                            Paths.get(scriptPath,script).toString());

                    this.scriptMap.put(script, container);
                }

                return container;
            }
        }
    }

    static class NoCache implements JRubyCache
    {
        private String scriptPath;
        private final Object lock = new Object();

        NoCache(String scriptPath) {
            this.scriptPath = scriptPath;
        }

        public ScriptingContainer getScript(String identifier) throws Exception {
            synchronized (this.lock) {
                String script = identifier + ".rb";
                ScriptingContainer container = new ScriptingContainer();
                container.runScriptlet(PathType.ABSOLUTE,
                        Paths.get(scriptPath,script).toString());
                return container;
            }
        }
    }
}
