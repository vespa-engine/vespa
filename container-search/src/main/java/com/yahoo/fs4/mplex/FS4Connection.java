// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.fs4.mplex;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.BufferTooSmallException;
import com.yahoo.fs4.PacketDecoder;
import com.yahoo.fs4.PacketListener;
import com.yahoo.io.Connection;
import com.yahoo.io.Listener;
import com.yahoo.log.LogLevel;

/**
 *
 * This class is used to represent a connection to an fdispatch
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class FS4Connection implements Connection
{
    private static Logger log = Logger.getLogger(FS4Connection.class.getName());
    private Backend backend;
    private Listener listener;
    private SocketChannel channel;

    private boolean shouldWrite = false;

    private static int idCounter = 1;
    private int idNumber;
    private int maxInitialSize = 1024;

    // outbound data
    private ByteBuffer writeBuffer;
    private LinkedList<ByteBuffer> writeBufferList = new LinkedList<>();

    // inbound data
    private ByteBuffer fixedReadBuffer = ByteBuffer.allocateDirect(256 * 1024);
    private ByteBuffer readBuffer = fixedReadBuffer;

    private volatile boolean valid = true;

    private final PacketListener packetListener;


    /**
     * Create an FS4 Connection.
     */
    public FS4Connection (SocketChannel channel, Listener listener, Backend backend, PacketListener packetListener) {
        this.backend = backend;
        this.listener = listener;
        this.channel = channel;
        this.idNumber = idCounter++;
        this.packetListener = packetListener;

        log.log(Level.FINER, "new: "+this+", id="+idNumber + ", address=" + backend.getAddress());
    }


    /**
     * Packet sending interface.
     */
    public void sendPacket (BasicPacket packet, Integer channelId) throws IOException {
        ByteBuffer buffer = packet.grantEncodingBuffer(channelId.intValue(), maxInitialSize);
        ByteBuffer viewForPacketListener = buffer.slice();
        synchronized (this) {
            if (!(valid && channel.isOpen())) {
                throw new IllegalStateException("Connection is not valid. " +
                        "Address = " + backend.getAddress()  +
                        ", valid = " + valid +
                        ", isOpen = " + channel.isOpen());
            }

            if (buffer.capacity() > maxInitialSize) {
                maxInitialSize = buffer.limit();
            }
            if (writeBuffer == null) {
                writeBuffer = buffer;
            } else {
                writeBufferList.addLast(buffer);
                enableWrite();
            }
            write();
        }

        if (packetListener != null)
            packetListener.packetSent(backend.getChannel(channelId), packet, viewForPacketListener);
    }


    /**
     * The write event handler.  This can be called both from the client
     * thread and from the IO thread, so it needs to be synchronized.  It
     * assumes that IO is nonblocking, and will attempt to keep writing
     * data until the system won't accept more data.
     *
     */
    public synchronized void write () throws IOException {
        if (! channel.isOpen()) {
            throw new IllegalStateException("Channel not open in write(), address=" + backend.getAddress());
        }

        try {
            int bytesWritten = 0;
            boolean isFinished = false;
            do {
                // if writeBuffer is not set we need to fetch the next buffer
                if (writeBuffer == null) {

                    // if the list is empty, signal the selector we do not need
                    // to do any writing for a while yet and bail
                    if (writeBufferList.isEmpty()) {
                        disableWrite();
                        isFinished = true;
                        break;
                    }
                    writeBuffer = writeBufferList.removeFirst();
                }

                // invariants: we have a writeBuffer
                bytesWritten = channel.write(writeBuffer);

                // buffer drained so we forget it and see what happens when we
                // go around.  if indeed we go around
                if (!writeBuffer.hasRemaining()) {
                    writeBuffer = null;
                }
            } while (bytesWritten > 0);
            if (!isFinished) {
                enableWrite();
            }
        } catch (IOException e) {
            log.log(LogLevel.DEBUG, "Failed writing to channel for backend "  + backend.getAddress() +
                    ". Closing channel", e);
            try {
                close();
            } catch (IOException ignored) {}

            throw e;
        }
    }


    private void disableWrite() {
        if (shouldWrite) {
            listener.modifyInterestOpsBatch(this, SelectionKey.OP_WRITE, false);
            shouldWrite = false;
        }
    }


    private void enableWrite() {
        if (!shouldWrite) {
            listener.modifyInterestOps(this, SelectionKey.OP_WRITE, true);
            shouldWrite = true;
        }
    }



    public void read () throws IOException {
        if (! channel.isOpen()) {
            throw new IOException("Channel not open in read(), address=" + backend.getAddress());
        }

        int bytesRead = 0;

        do {
            try {
                if (readBuffer == fixedReadBuffer) {
                    bytesRead = channel.read(readBuffer);
                } else {
                    fixedReadBuffer.clear();
                    if (readBuffer.remaining() < fixedReadBuffer.capacity()) {
                        fixedReadBuffer.limit(readBuffer.remaining());
                    }
                    bytesRead = channel.read(fixedReadBuffer);
                    fixedReadBuffer.flip();
                    readBuffer.put(fixedReadBuffer);
                    fixedReadBuffer.clear();
                }
            }
            catch (IOException e) {
                // this is the "normal" way that connection closes.
                log.log(Level.FINER, "Read exception address=" + backend.getAddress() + " id="+idNumber+": "+
                        e.getClass().getName()+" / ", e);
                bytesRead = -1;
            }

            // end of file
            if (bytesRead == -1) {
                log.log(LogLevel.DEBUG, "Dispatch closed connection"
                        + " (id="+idNumber+", address=" + backend.getAddress() + ")");
                try {
                    close();
                } catch (Exception e) {
                    log.log(Level.WARNING, "Close failed, address=" + backend.getAddress(), e);
                }
            }

            // no more read
            if (bytesRead == 0) {
                // buffer too small?
                if (! readBuffer.hasRemaining()) {
                    log.fine("Buffer possibly too small, extending");
                    readBuffer.flip();
                    extendReadBuffer(readBuffer.capacity() * 2);
                }
            }

        } while (bytesRead > 0);

        readBuffer.flip();

        // hand off packet extraction
        extractPackets(readBuffer);
    }

    private void extractPackets(ByteBuffer readBuffer) {
        for (;;) {
            PacketDecoder.DecodedPacket packet = null;
            try {
                FS4Channel receiver = null;
                int queryId = PacketDecoder.sniffChannel(readBuffer);
                if (queryId == 0) {
                    if (PacketDecoder.isPongPacket(readBuffer))
                        receiver = backend.getPingChannel();
                }
                else {
                    receiver = backend.getChannel(Integer.valueOf(queryId));
                }
                packet = PacketDecoder.extractPacket(readBuffer);

                if (packet != null)
                    packetListener.packetReceived(receiver, packet.packet, packet.consumedBytes);
            }
            catch (BufferTooSmallException e) {
                log.fine("Unable to decode, extending readBuffer");
                extendReadBuffer(PacketDecoder.packetLength(readBuffer));
                return;
            }

            // break out of loop if we did not get a packet out of the
            // buffer so we can select and read some more
            if (packet == null) {

                // if the buffer has been cleared, we can do a reset
                // of the readBuffer
                if ((readBuffer.position() == 0)
                    && (readBuffer.limit() == readBuffer.capacity()))
                {
                    resetReadBuffer();
                }
                break;
            }

            backend.receivePacket(packet.packet);
        }
    }

    /**
     * This is called when we close the connection to do any
     * pending cleanup work.  Closing a connection marks it as
     * not valid.
     */
    public void close () throws IOException {
        valid = false;
        channel.close();
        log.log(Level.FINER, "invalidated id="+idNumber + " address=" + backend.getAddress());
    }

    /**
     * Upon asynchronous connect completion this method is called by
     * the Listener.
     */
    public void connect() throws IOException {
        throw new RuntimeException("connect() was called, address=" + backend.getAddress() + ".  "
                                   + "asynchronous connect in NIO is flawed!");
    }

    /**
     * Since we are performing an asynchronous connect we are initially
     * only interested in the <code>OP_CONNECT</code> event.
     */
    public int selectOps () {
        return SelectionKey.OP_READ;
    }

    /**
     * Return the underlying SocketChannel used by this connection.
     */
    public SocketChannel socketChannel() {
        return channel;
    }


    public String toString () {
        return FS4Connection.class.getName() + "/" + channel;
    }


    //============================================================
    //==== readbuffer management
    //============================================================


    /**
     * Extend the readBuffer.  Make a new buffer of the requested size
     * copy the contents of the readBuffer into it and assign reference
     * to readBuffer instance variable.
     *
     * <P>
     * <b>The readBuffer needs to be in "readable" (flipped) state before
     * this is called and it will be in the "writeable" state when it
     * returns.</b>
     */
    private void extendReadBuffer (int size) {
        // we specifically check this because packetLength() can return -1
        // and someone might alter the code so that we do in fact get -1
        // ...which never happens as the code is now
        //
        if (size == -1) {
            throw new RuntimeException("Invalid buffer size requested: -1");
        }

        // if we get a size that is smaller than the current
        // readBuffer capacity we just double it.  not sure how wise this
        // might be.
        //
        if (size < readBuffer.capacity()) {
            size = readBuffer.capacity() * 2;
        }

        ByteBuffer tmp = ByteBuffer.allocate(size);
        tmp.put(readBuffer);
        log.fine("Extended readBuffer to " + size + " bytes"
                 + "from " + readBuffer.capacity() + " bytes");
        readBuffer = tmp;
    }

    /**
     * Clear the readBuffer, and if temporarily allocated bigger
     * buffer is in use: ditch it and reset the reference to the
     * fixed readBuffer.
     */
    private void resetReadBuffer () {
        fixedReadBuffer.clear();
        if (readBuffer == fixedReadBuffer) {
            return;
        }
        log.fine("Resetting readbuffer");
        readBuffer = fixedReadBuffer;
    }

    /**
     * This method is used to determine whether the connection is still
     * viable or not.  All connections are initially valid, but they
     * become invalid if we close the connection or something bad happens
     * and the connection needs to be ditched.
     */
    public boolean isValid() {
        return valid;
    }

}
