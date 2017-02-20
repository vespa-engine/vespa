// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;


class Connection extends Target {

    private static Logger log = Logger.getLogger(Connection.class.getName());

    private static final int READ_SIZE  = 8192;
    private static final int READ_REDO  = 10;
    private static final int WRITE_SIZE = 8192;
    private static final int WRITE_REDO = 10;

    private static final int INITIAL   = 0;
    private static final int CONNECTED = 1;
    private static final int CLOSED    = 2;

    private int           state      = INITIAL;
    private Queue         queue      = new Queue();
    private Queue         myQueue    = new Queue();
    private Buffer        input      = new Buffer(READ_SIZE * 2);
    private Buffer        output     = new Buffer(WRITE_SIZE * 2);
    private int           maxInputSize  = 64*1024;
    private int           maxOutputSize = 64*1024;
    private Map<Integer, ReplyHandler> replyMap = new HashMap<>();
    private Map<TargetWatcher, TargetWatcher> watchers = new IdentityHashMap<>();
    private int           activeReqs = 0;
    private int           writeWork  = 0;
    private Transport     parent;
    private Supervisor    owner;
    private Spec          spec;
    private SocketChannel channel;
    private boolean       server;
    private AtomicLong    requestId = new AtomicLong(0);
    private SelectionKey  selectionKey;
    private Exception     lostReason = null;

    private void setState(int state) {
        if (state <= this.state) {
            log.log(Level.WARNING, "Bogus state transition: " + this.state + "->" + state);
            return;
        }
        boolean live = (this.state == INITIAL && state == CONNECTED);
        boolean down = (this.state != CLOSED && state == CLOSED);
        boolean fini;
        boolean pendingWrite;
        synchronized (this) {
            this.state = state;
            fini = down && (activeReqs == 0);
            pendingWrite = (writeWork > 0);
        }
        if (live) {
            if (pendingWrite) {
                enableWrite();
            }
            owner.sessionLive(this);
        }
        if (down) {
            for (ReplyHandler rh : replyMap.values()) {
                rh.handleConnectionDown();
            }
            for (TargetWatcher watcher : watchers.values()) {
                watcher.notifyTargetInvalid(this);
            }
            owner.sessionDown(this);
        }
        if (fini) {
            owner.sessionFini(this);
        }
    }

    public Connection(Transport parent, Supervisor owner,
                      SocketChannel channel) {

        this.parent = parent;
        this.owner = owner;
        this.channel = channel;
        server = true;
        owner.sessionInit(this);
    }

    public Connection(Transport parent, Supervisor owner, Spec spec, Object context) {
        super(context);
        this.parent = parent;
        this.owner = owner;
        this.spec = spec;
        server = false;
        owner.sessionInit(this);
    }

    public void setMaxInputSize(int bytes) {
        maxInputSize = bytes;
    }

    public void setMaxOutputSize(int bytes) {
        maxOutputSize = bytes;
    }

    public Transport transport() {
        return parent;
    }

    public int allocateKey() {
        long v = requestId.getAndIncrement();
        v = v*2 + (server ? 1 : 0);
        int i = (int)(v & 0x7fffffff);
        return i;
    }

    public synchronized boolean cancelReply(ReplyHandler handler) {
        if (state == CLOSED) {
            return false;
        }
        ReplyHandler stored = replyMap.remove(handler.key());
        if (stored != handler) {
            if (stored != null) {
                replyMap.put(handler.key(), stored);
            }
            return false;
        }
        return true;
    }

    public boolean postPacket(Packet packet, ReplyHandler handler) {
        boolean accepted = false;
        boolean enableWrite = false;
        synchronized (this) {
            if (state <= CONNECTED) {
                enableWrite = (writeWork == 0 && state == CONNECTED);
                queue.enqueue(packet);
                writeWork++;
                accepted = true;
                if (handler != null) {
                    replyMap.put(handler.key(), handler);
                }
            }
        }
        if (enableWrite) {
            parent.enableWrite(this);
        }
        return accepted;
    }

    public boolean postPacket(Packet packet) {
        return postPacket(packet, null);
    }

    public Connection connect() {
        if (spec == null || spec.malformed()) {
            setLostReason(new IllegalArgumentException("jrt: malformed or missing spec"));
            return this;
        }
        try {
            channel = SocketChannel.open(spec.connectAddress());
        } catch (Exception e) {
            setLostReason(e);
        }
        return this;
    }

    public boolean init(Selector selector) {
        if (channel == null) {
            return false;
        }
        try {
            channel.configureBlocking(false);
            channel.socket().setTcpNoDelay(true);
            selectionKey = channel.register(selector,
                                            SelectionKey.OP_READ,
                                            this);
        } catch (Exception e) {
            log.log(Level.WARNING, "Error initializing connection", e);
            setLostReason(e);
            return false;
        }
        setState(CONNECTED);
        return true;
    }

    public void enableWrite() {
        selectionKey.interestOps(selectionKey.interestOps()
                                 | SelectionKey.OP_WRITE);
    }

    public void disableWrite() {
        selectionKey.interestOps(selectionKey.interestOps()
                                 & ~SelectionKey.OP_WRITE);
    }

    public void read() throws IOException {
        boolean doneRead = false;
        for (int i = 0; !doneRead && i < READ_REDO; i++) {
            ByteBuffer wb = input.getChannelWritable(READ_SIZE);
            if (channel.read(wb) == -1) {
                throw new IOException("jrt: Connection closed by peer");
            }
            doneRead = (wb.remaining() > 0);
            ByteBuffer rb = input.getReadable();
            while (true) {
                PacketInfo info = PacketInfo.getPacketInfo(rb);
                if (info == null || info.packetLength() > rb.remaining()) {
                    break;
                }
                owner.readPacket(info);
                Packet packet;
                try {
                    packet = info.decodePacket(rb);
                } catch (RuntimeException e) {
                    log.log(Level.WARNING, "got garbage; closing connection: " + toString());
                    throw new IOException("jrt: decode error", e);
                }
                ReplyHandler handler;
                synchronized (this) {
                    handler = replyMap.remove(packet.requestId());
                }
                if (handler != null) {
                    handler.handleReply(packet);
                } else {
                    owner.handlePacket(this, packet);
                }
            }
        }
        if (maxInputSize > 0) {
            input.shrink(maxInputSize);
        }
    }

    public void write() throws IOException {
        synchronized (this) {
            queue.flush(myQueue);
        }
        for (int i = 0; i < WRITE_REDO; i++) {
            while (output.bytes() < WRITE_SIZE) {
                Packet packet = (Packet) myQueue.dequeue();
                if (packet == null) {
                    break;
                }
                PacketInfo info = packet.getPacketInfo();
                ByteBuffer wb = output.getWritable(info.packetLength());
                owner.writePacket(info);
                info.encodePacket(packet, wb);
            }
            ByteBuffer rb = output.getChannelReadable();
            if (rb.remaining() == 0) {
                break;
            }
            channel.write(rb);
            if (rb.remaining() > 0) {
                break;
            }
        }
        boolean disableWrite;
        synchronized (this) {
            writeWork = queue.size()
                + myQueue.size()
                + ((output.bytes() > 0) ? 1 : 0);
            disableWrite = (writeWork == 0);
        }
        if (disableWrite) {
            disableWrite();
        }
        if (maxOutputSize > 0) {
            output.shrink(maxOutputSize);
        }
    }

    public void fini() {
        setState(CLOSED);
        if (selectionKey != null) {
            selectionKey.cancel();
        }
    }

    public boolean isClosed() {
        return (state == CLOSED);
    }

    public boolean hasSocket() {
        return (channel != null);
    }

    public void closeSocket() {
        if (channel != null) {
            try {
                channel.socket().close();
            } catch (Exception e) {
                log.log(Level.WARNING, "Error closing connection", e);
            }
        }
    }

    public void setLostReason(Exception e) {
        if (lostReason == null) {
            lostReason = e;
        }
    }

    public TieBreaker startRequest() {
        synchronized (this) {
            activeReqs++;
        }
        return new TieBreaker();
    }

    public boolean completeRequest(TieBreaker done) {
        boolean signalFini = false;
        synchronized (this) {
            if (!done.first()) {
                return false;
            }
            if (--activeReqs == 0 && state == CLOSED) {
                signalFini = true;
            }
        }
        if (signalFini) {
            owner.sessionFini(this);
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Methods defined in the Target superclass
    ///////////////////////////////////////////////////////////////////////////

    public boolean isValid() {
        return (state != CLOSED);
    }

    public Exception getConnectionLostReason() {
        return lostReason;
    }

    public boolean isClient() {
        return !server;
    }

    public boolean isServer() {
        return server;
    }

    public void invokeSync(Request req, double timeout) {
        SingleRequestWaiter waiter = new SingleRequestWaiter();
        invokeAsync(req, timeout, waiter);
        waiter.waitDone();
    }

    public void invokeAsync(Request req, double timeout,
                            RequestWaiter waiter) {
        if (timeout < 0.0) {
            timeout = 0.0;
        }
        new InvocationClient(this, req, timeout, waiter).invoke();
    }

    public boolean invokeVoid(Request req) {
        return postPacket(new RequestPacket(Packet.FLAG_NOREPLY,
                                            allocateKey(),
                                            req.methodName(),
                                            req.parameters()));
    }

    public synchronized boolean addWatcher(TargetWatcher watcher) {
        if (state == CLOSED) {
            return false;
        }
        watchers.put(watcher, watcher);
        return true;
    }

    public synchronized boolean removeWatcher(TargetWatcher watcher) {
        if (state == CLOSED) {
            return false;
        }
        watchers.remove(watcher);
        return true;
    }

    public void close() {
        parent.closeConnection(this);
    }

    public String toString() {
        if (channel != null) {
            return "Connection { " + channel.socket() + " }";
        }
        return "Connection { no socket, spec " + spec + " }";
    }
}
