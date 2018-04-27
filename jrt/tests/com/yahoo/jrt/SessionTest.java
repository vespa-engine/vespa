// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionTest implements SessionHandler {

    private static class Session {
        private static int     cnt   = 0;
        private static boolean error = false;

        private int     value   = 0;
        private boolean gotInit = false;
        private boolean gotLive = false;
        private boolean gotDown = false;
        private boolean gotFini = false;

        private static synchronized void add() {
            cnt++;
        }

        private static synchronized void sub() {
            cnt--;
        }

        public Session() {
            add();
        }

        public void init() {
            if (gotInit || gotLive || gotDown || gotFini) {
                setError();
            }
            gotInit = true;
        }

        public void live() {
            if (!gotInit || gotLive || gotDown || gotFini) {
                setError();
            }
            gotLive = true;
        }

        public void touch() {
            if (!gotInit || gotFini) {
                setError();
            }
        }

        public int value() {
            if (!gotInit || gotFini) {
                setError();
            }
            return value;
        }

        public void value(int value) {
            if (!gotInit || gotFini) {
                setError();
            }
            this.value = value;
        }

        public void down() {
            if (!gotInit || gotDown || gotFini) {
                setError();
            }
            gotDown = true;
        }

        public void fini() {
            if (!gotInit || !gotDown || gotFini) {
                setError();
            }
            gotFini = true;
            sub();
        }

        public static int cnt() {
            return cnt;
        }

        public static void setError() {
            error = true;
            Throwable e = new RuntimeException("ERROR TRACE");
            e.printStackTrace();
        }

        public static boolean getError() {
            return error;
        }

        public static void reset() {
            error = false;
            cnt = 0;
        }
    }

    Test.Orb      server;
    Acceptor      acceptor;
    Test.Orb      client;
    Target        target;
    Test.Receptor receptor;

    @Before
    public void setUp() throws ListenFailedException {
        Session.reset();
        server   = new Test.Orb(new Transport());
        server.setSessionHandler(this);
        client   = new Test.Orb(new Transport());
        client.setSessionHandler(this);
        acceptor = server.listen(new Spec(Test.PORT));
        target   = client.connect(new Spec("localhost", Test.PORT),
                                  new Session());

        server.addMethod(new Method("set", "i", "", this,
                                    "rpc_set"));
        server.addMethod(new Method("get", "", "i", this,
                                    "rpc_get"));
        server.addMethod(new Method("call_detach", "", "", this,
                                    "rpc_call_detach"));
        client.addMethod(new Method("detach", "", "", this,
                                    "rpc_detach"));
        receptor = new Test.Receptor();
    }

    @After
    public void tearDown() {
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    public void handleSessionInit(Target t) {
        Object ctx = t.getContext();
        if (t.isClient()) {
            if (ctx == null) {
                Session.setError();
            }
        }
        if (t.isServer()) {
            if (ctx != null) {
                Session.setError();
            }
            t.setContext(new Session());
        }
        Session s = (Session) t.getContext();
        if (s == null) {
            Session.setError();
        } else {
            s.init();
        }
    }

    public void handleSessionLive(Target t) {
        Session s = (Session) t.getContext();
        if (s == null) {
            Session.setError();
        } else {
            s.live();
        }
    }

    public void handleSessionDown(Target t) {
        Session s = (Session) t.getContext();
        if (s == null) {
            Session.setError();
        } else {
            s.down();
        }
    }

    public void handleSessionFini(Target t) {
        Session s = (Session) t.getContext();
        if (s == null) {
            Session.setError();
        } else {
            s.fini();
        }
    }

    public void rpc_set(Request req) {
        Session s = (Session) req.target().getContext();
        s.value(req.parameters().get(0).asInt32());
    }

    public void rpc_get(Request req) {
        Session s = (Session) req.target().getContext();
        req.returnValues().add(new Int32Value(s.value()));
    }

    public void rpc_call_detach(Request req) {
        Session s = (Session) req.target().getContext();
        s.touch();
        req.target().invokeVoid(new Request("detach"));
    }

    public void rpc_detach(Request req) {
        Session s = (Session) req.target().getContext();
        if (s == null) {
            Session.setError();
        } else {
            s.touch();
        }
        req.detach();
        receptor.put(req);
    }

    public void waitState(int sessionCount,
                          int serverInitCount,
                          int serverLiveCount,
                          int serverDownCount,
                          int serverFiniCount,
                          int clientInitCount,
                          int clientLiveCount,
                          int clientDownCount,
                          int clientFiniCount) {
        server.transport().sync().sync();
        client.transport().sync().sync();
        for (int i = 0; i < 100; i++) {
            if ((sessionCount    == Session.cnt()    || sessionCount    < 0) &&
                (serverInitCount == server.initCount || serverInitCount < 0) &&
                (serverLiveCount == server.liveCount || serverLiveCount < 0) &&
                (serverDownCount == server.downCount || serverDownCount < 0) &&
                (serverFiniCount == server.finiCount || serverFiniCount < 0) &&
                (clientInitCount == client.initCount || clientInitCount < 0) &&
                (clientLiveCount == client.liveCount || clientLiveCount < 0) &&
                (clientDownCount == client.downCount || clientDownCount < 0) &&
                (clientFiniCount == client.finiCount || clientFiniCount < 0)) {
                break;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        server.transport().sync().sync();
        client.transport().sync().sync();
    }

    @org.junit.Test
    public void testConnDownLast() {
        waitState(2, 1, 1, 0, 0, 1, 1, 0, 0);
        assertEquals(2, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(0, server.downCount);
        assertEquals(0, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(0, client.downCount);
        assertEquals(0, client.finiCount);

        Request req = new Request("get");
        target.invokeSync(req, 5.0);
        assertEquals(0, req.returnValues().get(0).asInt32());

        req = new Request("set");
        req.parameters().add(new Int32Value(42));
        target.invokeSync(req, 5.0);
        assertTrue(!req.isError());

        req = new Request("get");
        target.invokeSync(req, 5.0);
        assertEquals(42, req.returnValues().get(0).asInt32());

        assertEquals(2, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(0, server.downCount);
        assertEquals(0, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(0, client.downCount);
        assertEquals(0, client.finiCount);

        target.close();
        waitState(0, 1, 1, 1, 1, 1, 1, 1, 1);
        assertEquals(0, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(1, server.downCount);
        assertEquals(1, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(1, client.downCount);
        assertEquals(1, client.finiCount);
        assertFalse(Session.getError());
    }

    @org.junit.Test
    public void testReqDoneLast() {
        waitState(2, 1, 1, 0, 0, 1, 1, 0, 0);
        assertEquals(2, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(0, server.downCount);
        assertEquals(0, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(0, client.downCount);
        assertEquals(0, client.finiCount);

        Request req = new Request("get");
        target.invokeSync(req, 5.0);
        assertEquals(0, req.returnValues().get(0).asInt32());

        req = new Request("set");
        req.parameters().add(new Int32Value(42));
        target.invokeSync(req, 5.0);
        assertTrue(!req.isError());

        req = new Request("get");
        target.invokeSync(req, 5.0);
        assertEquals(42, req.returnValues().get(0).asInt32());

        assertEquals(2, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(0, server.downCount);
        assertEquals(0, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(0, client.downCount);
        assertEquals(0, client.finiCount);

        req = new Request("call_detach");
        target.invokeSync(req, 5.0);
        assertTrue(!req.isError());
        Request detached = (Request) receptor.get();

        target.close();
        waitState(1, 1, 1, 1, 1, 1, 1, 1, 0);
        assertEquals(1, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(1, server.downCount);
        assertEquals(1, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(1, client.downCount);
        assertEquals(0, client.finiCount);

        detached.returnRequest();
        waitState(0, 1, 1, 1, 1, 1, 1, 1, 1);
        assertEquals(0, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(1, server.downCount);
        assertEquals(1, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(1, client.downCount);
        assertEquals(1, client.finiCount);
        assertFalse(Session.getError());
    }

    @org.junit.Test
    public void testNeverLive() {
        waitState(2, 1, 1, 0, 0, 1, 1, 0, 0);
        assertEquals(2, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(0, server.downCount);
        assertEquals(0, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(0, client.downCount);
        assertEquals(0, client.finiCount);

        target.close();
        waitState(0, 1, 1, 1, 1, 1, 1, 1, 1);
        assertEquals(0, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(1, server.downCount);
        assertEquals(1, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(1, client.downCount);
        assertEquals(1, client.finiCount);

        Target bogus = client.connect(new Spec("bogus"),
                                          new Session());
        waitState(0, 1, 1, 1, 1, 2, 1, 2, 2);
        assertEquals(0, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(1, server.downCount);
        assertEquals(1, server.finiCount);
        assertEquals(2, client.initCount);
        assertEquals(1, client.liveCount); // <--- NB
        assertEquals(2, client.downCount);
        assertEquals(2, client.finiCount);
        assertFalse(Session.getError());
    }

    @org.junit.Test
    public void testTransportDown() {
        waitState(2, 1, 1, 0, 0, 1, 1, 0, 0);
        assertEquals(2, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(0, server.downCount);
        assertEquals(0, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(0, client.downCount);
        assertEquals(0, client.finiCount);

        server.transport().shutdown().join();

        waitState(0, 1, 1, 1, 1, 1, 1, 1, 1);
        assertEquals(0, Session.cnt());
        assertEquals(1, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(1, server.downCount);
        assertEquals(1, server.finiCount);
        assertEquals(1, client.initCount);
        assertEquals(1, client.liveCount);
        assertEquals(1, client.downCount);
        assertEquals(1, client.finiCount);

        target = client.connect(new Spec("localhost", Test.PORT),
                                new Session());

        waitState(0, 2, 1, 2, 2, 2, -1, 2, 2);
        assertEquals(0, Session.cnt());
        assertEquals(2, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(2, server.downCount);
        assertEquals(2, server.finiCount);
        assertEquals(2, client.initCount);
        int oldClientLive = client.liveCount;
        assertEquals(2, client.downCount);
        assertEquals(2, client.finiCount);

        client.transport().shutdown().join();

        target = client.connect(new Spec("localhost", Test.PORT),
                                new Session());

        waitState(0, 2, 1, 2, 2, 3, oldClientLive, 3, 3);
        assertEquals(0, Session.cnt());
        assertEquals(2, server.initCount);
        assertEquals(1, server.liveCount);
        assertEquals(2, server.downCount);
        assertEquals(2, server.finiCount);
        assertEquals(3, client.initCount);
        assertEquals(oldClientLive, client.liveCount);
        assertEquals(3, client.downCount);
        assertEquals(3, client.finiCount);
        assertFalse(Session.getError());
    }

}
