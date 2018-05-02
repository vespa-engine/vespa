// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.fs4.mplex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.ChannelTimeoutException;
import com.yahoo.fs4.Packet;
import com.yahoo.search.Query;



/**
 *
 * This class is used to represent a "channel" in the FS4 protocol.
 * A channel represents a session between a client and the fdispatch.
 * Internally this class has a  response queue used by the backend
 * for queueing up FS4 packets that belong to this channel (or
 * <em>session</em>, which might be a more appropriate name for it).
 *
 * <P>
 * Outbound packets are handed off to the FS4Connection.
 *
 * @author Bjorn Borud
 */
public class FS4Channel
{
    private static Logger log = Logger.getLogger(FS4Channel.class.getName());

    private Integer channelId;
    private Backend backend;
    volatile private BlockingQueue<BasicPacket> responseQueue;
    private Query query;
    private boolean isPingChannel = false;

    /** for unit testing.  do not use */
    protected FS4Channel () {
    }

    protected FS4Channel(Backend backend, Integer channelId) {
        this.channelId = channelId;
        this.backend = backend;
        this.responseQueue = new LinkedBlockingQueue<>();
    }

    static public FS4Channel createPingChannel(Backend backend) {
        FS4Channel pingChannel = new FS4Channel(backend, new Integer(0));
        pingChannel.isPingChannel = true;
        return pingChannel;
    }

    /** Set the query currently associated with this channel */
    public void setQuery(Query query) {
        this.query = query;
    }

    /** Get the query currently associated with this channel */
    public Query getQuery() {
        return query;
    }

    /**
     * @return returns an Integer representing the (fs4) channel id
     */
    public Integer getChannelId () {
        return channelId;
    }

    /**
     * Closes the channel
     */
    public void close () {
        BlockingQueue<BasicPacket> q = responseQueue;
        responseQueue = null;
        query = null;
        if (isPingChannel) {
            backend.removePingChannel();
        } else {
            backend.removeChannel(channelId);
        }
        if (q != null) {
            q.clear();
        }
    }

    /**
     * Legacy interface.
     */
    public boolean sendPacket(BasicPacket packet) throws InvalidChannelException, IOException {
        ensureValid();
        return backend.sendPacket(packet, channelId);
    }

    /**
     * Receives the given number of packets and returns them, OR
     * <ul>
     * <li>Returns a smaller number of packets if an error or eol packet is received
     * <li>Throws a ChannelTimeoutException if timeout occurs before all packets
     * are received. Packets received with the wrong channel id are ignored.
     * </ul>
     *
     * @param timeout the number of ms to attempt to get packets before throwing an exception
     * @param packetCount the number of packets to receive, or -1 to receive any number up to eol/error
     */
    public BasicPacket[] receivePackets(long timeout, int packetCount)
        throws InvalidChannelException, ChannelTimeoutException
    {
        ensureValid();

        List<BasicPacket> packets = new ArrayList<>(12);
        long startTime = SystemTimer.INSTANCE.milliTime();
        long timeLeft  = timeout;

        try {
            while (timeLeft >= 0) {
                BasicPacket p = nextPacket(timeLeft);
                if (p == null) throw new ChannelTimeoutException("Timed out");

                if (!isPingChannel && ((Packet)p).getChannel() != getChannelId().intValue()) {
                    log.warning("Ignoring received " + p + ", when excepting channel " + getChannelId());
                    continue;
                }

                packets.add(p);
                if (isLastPacket(p) || hasEnoughPackets(packetCount, packets)) {
                    BasicPacket[] packetArray = new BasicPacket[packets.size()];
                    packets.toArray(packetArray);
                    return packetArray;
                }

                // doing this last might save us one system call for the last
                // packet.
                timeLeft = timeout - (SystemTimer.INSTANCE.milliTime() - startTime);
            }
        }
        catch (InvalidChannelException e) {
            // nop.  if we get this we want to return the default
            // zero length packet array indicating that we have no
            // valid response
            log.info("FS4Channel was invalid. timeLeft="
                     + timeLeft + ", timeout=" + timeout);
        }
        catch (InterruptedException e) {
            log.info("FS4Channel was interrupted. timeLeft="
                     + timeLeft + ", timeout=" + timeout);
            Thread.currentThread().interrupt();
        }

        // default return, we only hit this if we timed out and
        // did not get the end of the packet stream
        throw new ChannelTimeoutException();
    }

    private static boolean hasEnoughPackets(int packetCount,List<BasicPacket> packets) {
        if (packetCount<0) return false;
        return packets.size()>=packetCount;
    }

    /**
     * Returns true if we will definitely receive more packets on this stream
     *
     * Shouldn't that be "_not_ receive more packets"?
     */
    private static boolean isLastPacket (BasicPacket packet) {
        if (packet instanceof com.yahoo.fs4.ErrorPacket) return true;
        if (packet instanceof com.yahoo.fs4.EolPacket) return true;
        if (packet instanceof com.yahoo.fs4.PongPacket) return true;
        return false;
    }

    /**
     * Return the next available packet from the response queue.  If there
     * are no packets available we wait a maximum of <code>timeout</code>
     * milliseconds before returning a <code>null</code>
     *
     * @param timeout Number of milliseconds to wait for a packet
     *                to become available.
     *
     * @return Returns the next available <code>BasicPacket</code> or
     *         <code>null</code> if we timed out.
     */
    public BasicPacket nextPacket(long timeout)
        throws InterruptedException, InvalidChannelException
    {
        return ensureValidQ().poll(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Add incoming packet to the response queue.  This is to be used
     * by the listener for placing incoming packets in the response
     * queue.
     *
     * @param packet BasicPacket to be placed in the response queue.
     *
     */
    protected void addPacket (BasicPacket packet)
        throws InterruptedException, InvalidChannelException
    {
        ensureValidQ().put(packet);
    }

    /**
     * A valid FS4Channel is one that has not yet been closed.
     *
     * @return Returns <code>true</code> if the FS4Channel is valid.
     */
    public boolean isValid () {
        return responseQueue != null;
    }

    /**
     * This method is called whenever we want to perform an operation
     * which assumes that the FS4Channel object is valid.  An exception
     * is thrown if the opposite turns out to be the case.
     *
     * @throws InvalidChannelException if the channel is no longer valid.
     */
    private void ensureValid () throws InvalidChannelException {
        if (isValid()) {
            return;
        }
        throw new InvalidChannelException("Channel is no longer valid");
    }

    /**
     * This method is called whenever we want to perform an operation
     * which assumes that the FS4Channel object is valid.  An exception
     * is thrown if the opposite turns out to be the case.
     *
     * @throws InvalidChannelException if the channel is no longer valid.
     */
    private BlockingQueue<BasicPacket> ensureValidQ () throws InvalidChannelException {
        BlockingQueue<BasicPacket> q = responseQueue;
        if (q != null) {
            return q;
        }
        throw new InvalidChannelException("Channel is no longer valid");
    }

    public String toString() {
        return "fs4 channel " + channelId + (isValid() ? " [valid]" : " [invalid]");
    }

}
