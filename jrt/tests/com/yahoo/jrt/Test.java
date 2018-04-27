// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.util.Arrays;

public class Test {

    @org.junit.Test
    public void testNothing() {}

    // www.random.org [2000, 9999]
    public static final int PORT   = 9741;
    public static final int PORT_0 = 5069;
    public static final int PORT_1 = 4935;
    public static final int PORT_2 = 8862;
    public static final int PORT_3 = 4695;
    public static final int PORT_4 = 6975;
    public static final int PORT_5 = 7186;
    public static final int PORT_6 = 7694;
    public static final int PORT_7 = 3518;
    public static final int PORT_8 = 3542;
    public static final int PORT_9 = 4954;

    /**
     * Supervisor extension with some extra statistics used for
     * testing.
     **/
    public static class Orb extends Supervisor {
        public int  initCount         = 0;
        public int  liveCount         = 0;
        public int  readRequestCount  = 0;
        public int  readReplyCount    = 0;
        public int  readErrorCount    = 0;
        public long readBytes         = 0;
        public int  writeRequestCount = 0;
        public int  writeReplyCount   = 0;
        public int  writeErrorCount   = 0;
        public long writeBytes        = 0;
        public int  downCount         = 0;
        public int  finiCount         = 0;

        public Orb(Transport t) {
            super(t);
        }

        public boolean checkReadCounts(int request, int reply, int error) {
            return (request == readRequestCount &&
                    reply == readReplyCount &&
                    error == readErrorCount);
        }

        public boolean checkWriteCounts(int request, int reply, int error) {
            return (request == writeRequestCount &&
                    reply == writeReplyCount &&
                    error == writeErrorCount);
        }

        public boolean checkLifeCounts(int init, int fini) {
            return (init == initCount && fini == finiCount);
        }

        public void sessionInit(Target target) {
            initCount++;
            super.sessionInit(target);
        }

        public void sessionLive(Target target) {
            liveCount++;
            super.sessionLive(target);
        }

        public void sessionDown(Target target) {
            downCount++;
            super.sessionDown(target);
        }

        public void sessionFini(Target target) {
            finiCount++;
            super.sessionFini(target);
        }

        public void readPacket(PacketInfo info) {
            if (info.packetCode() == Packet.PCODE_REQUEST) {
                readRequestCount++;
            } else if (info.packetCode() == Packet.PCODE_REPLY) {
                readReplyCount++;
            } else if (info.packetCode() == Packet.PCODE_ERROR) {
                readErrorCount++;
            }
            readBytes += info.packetLength();
            super.readPacket(info);
        }

        public void writePacket(PacketInfo info) {
            if (info.packetCode() == Packet.PCODE_REQUEST) {
                writeRequestCount++;
            } else if (info.packetCode() == Packet.PCODE_REPLY) {
                writeReplyCount++;
            } else if (info.packetCode() == Packet.PCODE_ERROR) {
                writeErrorCount++;
            }
            writeBytes += info.packetLength();
            super.writePacket(info);
        }
    }

    /**
     * A simple object used to wait for the completion of an
     * asynchronous request.
     **/
    public static class Waiter implements RequestWaiter {
        private boolean done = false;
        public boolean isDone() {
            return done;
        }
        public synchronized void handleRequestDone(Request req) {
            done = true;
            notify();
        }
        public synchronized void waitDone() {
            while (!isDone()) {
                try { wait(); } catch (InterruptedException e) {}
            }
        }
    }

    /**
     * A simple object used to make one thread wait until another
     * thread tells it to continue.
     **/
    public static class Barrier {
        private boolean broken = false;
        public synchronized void reset() {
            broken = false;
        }
        public synchronized void breakIt() {
            broken = true;
            notify();
        }
        public synchronized void waitFor() {
            while (!broken) {
                try { wait(); } catch (InterruptedException e) {}
            }
        }
    }

    /**
     * A simple object used to pass a single object from one thread to
     * another.
     **/
    public static class Receptor {
        private Object obj = null;
        public synchronized void reset() {
            obj = null;
        }
        public synchronized Object get() {
            while (obj == null) {
                try { wait(); } catch (InterruptedException e) {}
            }
            return obj;
        }
        public synchronized void put(Object obj) {
            this.obj = obj;
            notify();
        }
    }


    public static boolean equals(byte[][] a, byte[][] b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!Arrays.equals(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    public static boolean equals(Value a, Value b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.type() != b.type()) {
            return false;
        }
        switch (a.type()) {
        case Value.INT8:         return (a.asInt8() == b.asInt8());
        case Value.INT8_ARRAY:   return Arrays.equals(a.asInt8Array(),
                                                      b.asInt8Array());
        case Value.INT16:        return (a.asInt16() == b.asInt16());
        case Value.INT16_ARRAY:  return Arrays.equals(a.asInt16Array(),
                                                      b.asInt16Array());
        case Value.INT32:        return (a.asInt32() == b.asInt32());
        case Value.INT32_ARRAY:  return Arrays.equals(a.asInt32Array(),
                                                      b.asInt32Array());
        case Value.INT64:        return (a.asInt64() == b.asInt64());
        case Value.INT64_ARRAY:  return Arrays.equals(a.asInt64Array(),
                                                      b.asInt64Array());
        case Value.FLOAT:        return (a.asFloat() == b.asFloat());
        case Value.FLOAT_ARRAY:  return Arrays.equals(a.asFloatArray(),
                                                      b.asFloatArray());
        case Value.DOUBLE:       return (a.asDouble() == b.asDouble());
        case Value.DOUBLE_ARRAY: return Arrays.equals(a.asDoubleArray(),
                                                      b.asDoubleArray());
        case Value.DATA:         return Arrays.equals(a.asData(), b.asData());
        case Value.DATA_ARRAY:   return equals(a.asDataArray(),
                                               b.asDataArray());
        case Value.STRING:       return a.asString().equals(b.asString());
        case Value.STRING_ARRAY: return Arrays.equals(a.asStringArray(),
                                                      b.asStringArray());
        default: return false;
        }
    }

    public static boolean equals(Values a, Values b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

}
