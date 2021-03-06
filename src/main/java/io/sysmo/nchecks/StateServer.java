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

import com.ericsson.otp.erlang.OtpMbox;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;

import java.net.ServerSocket;
import java.net.Socket;

import java.nio.file.Paths;

/**
 * Created by seb on 18/10/15.
 *
 * Provide berkley db store for NChecks checks state.
 */
public class StateServer {

    public static final int DEFAULT_PORT = 9760;
    private static final String DB_NAME = "NCHECKS_STATES";
    private static Database DB;
    private static Environment ENV;
    private static Logger LOGGER;
    private static OtpMbox MBOX;
    private static StateServerSocket SERVER;
    private static final Object LOCK = new Object();
    private static boolean STARTED = false;

    public static void main(final String[] args) {
        StateServer.LOGGER = LoggerFactory.getLogger(StateServer.class);
        String dataDir = args[0];
        int port = Integer.parseInt(args[1]);

        try {
            StateServer.start(dataDir, port, null);
        } catch (IOException e) {
            StateServer.LOGGER.error(e.getMessage(), e);
        }
    }

    public static boolean isStarted() {
        return StateServer.STARTED;
    }

    public static synchronized void start(
            final String dataDir, int port,
            final OtpMbox mbox) throws IOException {
        StateServer.STARTED = true;
        if (port == 0) {
            port = StateServer.DEFAULT_PORT;
        }

        StateServer.LOGGER = LoggerFactory.getLogger(StateServer.class);
        StateServer.MBOX = mbox;

        // init db
        String home = Paths.get(dataDir, "states").toString();
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        StateServer.ENV = new Environment(new File(home), envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTemporary(true);
        StateServer.DB = StateServer.ENV.openDatabase(
                null, StateServer.DB_NAME, dbConfig);
        StateServer.LOGGER.info("database ok");

        StateServer.SERVER = new StateServerSocket(port);
        Thread serverThread = new Thread(SERVER);
        serverThread.start();
        StateServer.LOGGER.info("server listening on " + port);

    }

    public static void stop() {
        StateServer.STARTED = false;
        if (StateServer.MBOX != null) {
            StateServer.MBOX.exit("crash");
        }
        StateServer.DB.close();
        StateServer.ENV.close();
        StateServer.SERVER.stop();
        StateServer.MBOX.close();
        StateServer.LOGGER.info("end run");
    }

    public static byte[] getState(String key) {
        synchronized (StateServer.LOCK) {
            try {
                DatabaseEntry theKey = new DatabaseEntry(key.getBytes("UTF-8"));
                DatabaseEntry data = new DatabaseEntry();
                if (StateServer.DB.get(null, theKey, data,
                        LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                    return data.getData();
                } else {
                    // Must return an empty byte array, "null" will not pass
                    // ObjectInputStream on client side.
                    return new byte[0];
                }
            } catch (UnsupportedEncodingException e) {
                StateServer.LOGGER.warn(e.getMessage(), e);
                return new byte[0];
            }
        }
    }

    public static void setState(String key, byte[] value) {
        synchronized (StateServer.LOCK) {
            try {
                DatabaseEntry theKey = new DatabaseEntry(key.getBytes("UTF-8"));
                DatabaseEntry theData = new DatabaseEntry(value);
                StateServer.DB.put(null, theKey, theData);
            } catch (UnsupportedEncodingException e) {
                StateServer.LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    static class StateServerSocket implements Runnable {

        // server loop
        private ServerSocket server = null;
        private final Logger logger;
        private boolean normalShutdown = false;

        StateServerSocket(int port) throws IOException {
            this.logger = LoggerFactory.getLogger(this.getClass());
            this.server = new ServerSocket(port);
            this.logger.info("create state server socket");
        }

        public void stop() {
            this.logger.info("stop socket listener");
            if (this.server != null) {
                try {
                    this.normalShutdown = true;
                    this.server.close();
                    this.server = null;
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        @Override
        public void run() {
            this.logger.info("Start socket listener");
            while (true) {
                try {

                    Socket client = this.server.accept();
                    client.setTcpNoDelay(true);
                    Runnable clientRunnable = new ServerClientSocket(client);
                    Thread clientThread = new Thread(clientRunnable);
                    clientThread.start();
                    this.logger.debug("have accepted client");

                } catch (Exception | Error e) {
                    if (this.normalShutdown) {
                        this.logger.info(e.getMessage(), e);
                    } else {
                        this.logger.error(e.getMessage(), e);
                        if (this.server != null) {
                            try {
                                this.server.close();
                            } catch (IOException ignore) {
                                // ignore
                            }
                        }
                    }
                    break;
                }
            }
            StateServer.stop();
        }
    }

    static class ServerClientSocket implements Runnable {

        private final Socket socket;
        private final Logger logger;

        ServerClientSocket(Socket socket) {
            this.logger = LoggerFactory.getLogger(this.getClass());
            this.socket = socket;
            this.logger.debug("Accept client for socket: "
                    + socket.getInetAddress());
        }

        @Override
        public void run() {
            ObjectInputStream in = null;
            ObjectOutputStream out = null;
            try {

                // Why ???
                this.socket.getOutputStream().flush();
                //

                in = new ObjectInputStream(this.socket.getInputStream());
                out = new ObjectOutputStream(this.socket.getOutputStream());

                String key;
                byte[] bytes;

                StateMessage message;
                StateMessage reply;
                while (this.socket.isConnected()) {
                    try {

                        message = (StateMessage) in.readObject();

                        switch (message.getAction()) {
                            case StateMessage.SET:
                                key = message.getKey();
                                bytes = message.getObjectBytes();
                                StateServer.setState(key, bytes);
                                break;
                            case StateMessage.GET:
                                key = message.getKey();
                                bytes = StateServer.getState(key);
                                reply = new StateMessage(StateMessage.GET);
                                reply.setKey(key);
                                reply.setBytes(bytes);
                                out.writeObject(reply);
                                out.flush();
                                break;
                            default:
                            // nothing
                        }

                    } catch (IOException | ClassNotFoundException inner) {
                        break;
                    }
                }

            } catch (IOException e) {
                this.logger.warn(e.getMessage(), e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignore) {
                        // ignore
                    }
                }

                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignore) {
                        // ignore
                    }
                }

                try {
                    this.socket.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }
}
