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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by seb on 21/10/15.
 *
 * Allow the connexion to a StateServer
 */
public class StateClient implements Runnable {

    private static StateClient instance = null;
    private static ConcurrentHashMap<String, StateMessage> map = new ConcurrentHashMap<>();
    private static final int TIMEOUT = 2000;
    private Logger logger;
    private static final Object lock = new Object();
    private ObjectInputStream in = null;
    private ObjectOutputStream out = null;
    private Socket socket = null;
    private boolean normalShutdown = false;

    public static void start(InetAddress address, int port) throws Exception {
        synchronized (StateClient.lock) {
            if (StateClient.instance == null) {
                StateClient.instance = new StateClient(address, port);
                Thread thread = new Thread(StateClient.instance);
                thread.start();
            } else {
                StateClient.instance.logger.warn("State client already started");
            }
        }
    }

    public static void stop() {
        StateClient sc = StateClient.instance;
        sc.normalShutdown = true;

        if (sc.in != null) {
            try {
                sc.in.close();
            } catch (IOException inner) {
                // ignore
            }
        }

        if (sc.out != null) {
            try {
                sc.out.close();
            } catch (IOException inner) {
                // ignore
            }
        }

        if (sc.socket != null) {
            try {
                sc.socket.close();
            } catch (IOException inner) {
                // ignore
            }
        }
    }

    private StateClient(InetAddress address, int port) throws Exception {
        if (port == 0) {
            port = StateServer.DEFAULT_PORT;
        }
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.logger.info("will start with: " + address + port);

        try {
            InetSocketAddress inetAddress = new InetSocketAddress(address, port);
            this.socket = new Socket();
            this.socket.setKeepAlive(true);
            this.socket.setTcpNoDelay(true);
            this.socket.connect(inetAddress, StateClient.TIMEOUT);

            // Why???
            this.socket.getOutputStream().flush();
            // ???

            this.logger.debug("after socket connect: " + this.socket.isConnected());
            this.out = new ObjectOutputStream(this.socket.getOutputStream());
            this.logger.debug("after get output stream: ", this.out.toString());
            this.in = new ObjectInputStream(this.socket.getInputStream());
            this.logger.debug("after get input stream: " + this.in.toString());

        } catch (Exception e) {
            this.logger.error(e.getMessage(), e);
            if (this.out != null) {
                try {
                    this.out.close();
                } catch (IOException inner) {
                    // ignore
                }
            }

            if (this.in != null) {
                try {
                    this.in.close();
                } catch (IOException inner) {
                    // ignore
                }
            }

            if (this.socket != null) {
                try {
                    socket.close();
                } catch (IOException inner) {
                    // ignore
                }
            }

            throw e;
        }

    }

    public static void setState(StateMessage msg) {
        synchronized (StateClient.lock) {
            try {
                StateClient.instance.out.writeObject(msg);
                StateClient.instance.out.flush();
            } catch (IOException e) {
                StateClient.instance.logger.error(e.getMessage(), e);
            }
        }
    }

    public static Object getState(StateMessage msg) {
        StateClient.map.put(msg.getKey(), msg);
        synchronized (StateClient.lock) {
            try {
                StateClient.instance.out.writeObject(msg);
                StateClient.instance.out.flush();
            } catch (IOException e) {
                StateClient.instance.logger.error(e.getMessage(), e);
                return null;
            }
        }

        synchronized (msg) {
            try {
                msg.wait(2000);
            } catch (InterruptedException e) {
                StateClient.instance.logger.error(e.getMessage(), e);
                StateClient.map.remove(msg.getKey());
                return null;
            }

            StateMessage reply = StateClient.map.remove(msg.getKey());
            if (reply == msg) {
                return null;
            } else {
                return reply.getObject();
            }
        }
    }

    @Override
    public void run() {
        StateMessage message;
        this.logger.info("start run");

        while (this.socket.isConnected()) {
            try {
                message = (StateMessage) this.in.readObject();

                if (message.getAction() == StateMessage.GET) {
                    String key = message.getKey();
                    Object caller = StateClient.map.get(key);
                    StateClient.map.put(key, message);
                    synchronized (caller) {
                        caller.notify();
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (this.normalShutdown) {
                    this.logger.info(e.getMessage(), e);
                } else {
                    this.logger.error(e.getMessage(), e);
                    StateClient.stop();
                }
                break;
            }
        }
    }
}
