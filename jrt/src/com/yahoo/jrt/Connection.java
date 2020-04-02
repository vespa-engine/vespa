// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;


class Connection extends Target {

    private static final Logger log = Logger.getLogger(Connection.class.getName());

    private static final int READ_SIZE  = 32768;
    private static final int READ_REDO  = 10;
    private static final int WRITE_SIZE = 32768;
    private static final int WRITE_REDO = 10;

    private static final int INITIAL    = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECTED  = 2;
    private static final int CLOSED     = 3;

    private int state = INITIAL;
    private final Queue  queue   = new Queue();
    private final Queue  myQueue = new Queue();
    private final Buffer input   = new Buffer(READ_SIZE * 2);
    private final Buffer output  = new Buffer(WRITE_SIZE * 2);
    private int maxInputSize  = 64*1024;
    private int maxOutputSize = 64*1024;
    private final boolean tcpNoDelay;
    private final Map<Integer, ReplyHandler> replyMap = new HashMap<>();
    private final Map<TargetWatcher, TargetWatcher> watchers = new IdentityHashMap<>();
    private int activeReqs = 0;
    private int writeWork  = 0;
    private boolean pendingHandshakeWork = false;
    private final TransportThread parent;
    private final Supervisor owner;
    private final Spec spec;
    private CryptoSocket socket;
    private int readSize = READ_SIZE;
    private final boolean server;
    private final AtomicLong requestId = new AtomicLong(0);
    private SelectionKey selectionKey;
    private Exception lostReason = null;

    private void setState(int state) {
        if (state <= this.state) {
            log.log(Level.WARNING, "Bogus state transition: " + this.state + "->" + state);
            return;
        }
        boolean live = (state == CONNECTED);
        boolean down = (state == CLOSED);
        boolean fini;
        boolean pendingWrite;
        synchronized (this) {
            this.state = state;
            fini = down && (activeReqs == 0);
            pendingWrite = (writeWork > 0);
        }
        if (live) {
            enableRead();
            if (pendingWrite) {
                enableWrite();
            } else {
                disableWrite();
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

    public Connection(TransportThread parent, Supervisor owner,
                      SocketChannel channel, boolean tcpNoDelay) {

        this.parent = parent;
        this.owner = owner;
        this.socket = parent.transport().createServerCryptoSocket(channel);
        this.spec = null;
        this.tcpNoDelay = tcpNoDelay;
        server = true;
        owner.sessionInit(this);
    }

    public Connection(TransportThread parent, Supervisor owner, Spec spec, Object context, boolean tcpNoDelay) {
        super(context);
        this.parent = parent;
        this.owner = owner;
        this.spec = spec;
        this.tcpNoDelay = tcpNoDelay;
        server = false;
        owner.sessionInit(this);
    }

    public void setMaxInputSize(int bytes) {
        maxInputSize = bytes;
    }

    public void setMaxOutputSize(int bytes) {
        maxOutputSize = bytes;
    }

    public TransportThread transportThread() {
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
            socket = parent.transport().createClientCryptoSocket(SocketChannel.open(spec.resolveAddress()), spec);
        } catch (Exception e) {
            setLostReason(e);
        }
        return this;
    }

    public boolean init(Selector selector) {
        if (!hasSocket()) {
            return false;
        }
        try {
            socket.channel().configureBlocking(false);
            socket.channel().socket().setTcpNoDelay(tcpNoDelay);
            selectionKey = socket.channel().register(selector,
                    SelectionKey.OP_READ | SelectionKey.OP_WRITE,
                    this);
        } catch (Exception e) {
            log.log(Level.WARNING, "Error initializing connection", e);
            setLostReason(e);
            return false;
        }
        setState(CONNECTING);
        return true;
    }

    public void enableRead() {
        selectionKey.interestOps(selectionKey.interestOps()
                                 | SelectionKey.OP_READ);
    }

    public void disableRead() {
        selectionKey.interestOps(selectionKey.interestOps()
                                 & ~SelectionKey.OP_READ);
    }

    public void enableWrite() {
        selectionKey.interestOps(selectionKey.interestOps()
                                 | SelectionKey.OP_WRITE);
    }

    public void disableWrite() {
        selectionKey.interestOps(selectionKey.interestOps()
                                 & ~SelectionKey.OP_WRITE);
    }

    private void handshake() throws IOException {
        if (pendingHandshakeWork) {
            return;
        }
        switch (socket.handshake()) {
        case DONE:
            if (socket.getMinimumReadBufferSize() > readSize) {
                readSize = socket.getMinimumReadBufferSize();
            }
            setState(CONNECTED);
            while (socket.drain(input.getChannelWritable(readSize)) > 0) {
                handlePackets();
            }
            break;
        case NEED_READ:
            enableRead();
            disableWrite();
            break;
        case NEED_WRITE:
            disableRead();
            enableWrite();
            break;
        case NEED_WORK:
            disableRead();
            disableWrite();
            pendingHandshakeWork = true;
            parent.transport().doHandshakeWork(this);
            break;
        }
    }

    public void doHandshakeWork() {
       socket.doHandshakeWork();
    }

    public void handleHandshakeWorkDone() throws IOException {
        if (!pendingHandshakeWork) {
            throw new IllegalStateException("jrt: got unwanted handshake work done event");
        }
        pendingHandshakeWork = false;
        if (state == CONNECTING) {
            handshake();
        } else {
            throw new IOException("jrt: got handshake work done event in incompatible state: " + state);
        }
    }

    private void handlePackets() throws IOException {
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

    private void read() throws IOException {
        boolean doneRead = false;
        for (int i = 0; !doneRead && i < READ_REDO; i++) {
            ByteBuffer wb = input.getChannelWritable(readSize);
            if (socket.read(wb) == -1) {
                throw new IOException("jrt: Connection closed by peer");
            }
            doneRead = (wb.remaining() > 0);
            handlePackets();
        }
        while (socket.drain(input.getChannelWritable(readSize)) > 0) {
            handlePackets();
        }
        if (maxInputSize > 0) {
            input.shrink(maxInputSize);
        }
    }

    public void handleReadEvent() throws IOException {
        if (state == CONNECTED) {
            read();
        } else if (state == CONNECTING) {
            handshake();
        } else {
            throw new IOException("jrt: got read event in incompatible state: " + state);
        }
    }

    private void write() throws IOException {
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
            socket.write(rb);
            if (rb.remaining() > 0) {
                break;
            }
        }
        int myWriteWork = 0;
        if (output.bytes() > 0) {
            myWriteWork++;
        }
        if (socket.flush() == CryptoSocket.FlushResult.NEED_WRITE) {
            myWriteWork++;
        }
        boolean disableWrite;
        synchronized (this) {
            writeWork = queue.size()
                        + myQueue.size()
                        + myWriteWork;
            disableWrite = (writeWork == 0);
        }
        if (disableWrite) {
            disableWrite();
        }
        if (maxOutputSize > 0) {
            output.shrink(maxOutputSize);
        }
    }

    public void handleWriteEvent() throws IOException {
        if (state == CONNECTED) {
            write();
        } else if (state == CONNECTING) {
            handshake();
        } else {
            throw new IOException("jrt: got write event in incompatible state: " + state);
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
        return ((socket != null) && (socket.channel() != null));
    }

    public void closeSocket() {
        if (hasSocket()) {
            try {
                socket.channel().socket().close();
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

    @Override
    public Optional<SecurityContext> getSecurityContext() {
        return Optional.ofNullable(socket)
                .flatMap(CryptoSocket::getSecurityContext);
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
        if (hasSocket()) {
            return "Connection { " + socket.channel().socket() + " }";
        }
        return "Connection { no socket, spec " + spec + " }";
    }
}
