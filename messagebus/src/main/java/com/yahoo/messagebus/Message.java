// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.messagebus.routing.Route;

/**
 * <p>A message is a child of Routable, it is not a reply, and it has a sequencing identifier. Furthermore, a message
 * contains a retry counter that holds what retry the message is currently on. See the method comment {@link #getRetry}
 * for more information.</p>
 *
 * @author Simon Thoresen Hult
 */
public abstract class Message extends Routable {

    private Route route = null;
    private long timeReceived = 0;
    private long timeRemaining = 0;
    private boolean retryEnabled = true;
    private int retry = 0;

    @Override
    public void swapState(Routable rhs) {
        super.swapState(rhs);
        if (rhs instanceof Message) {
            Message msg = (Message)rhs;

            Route route = this.route;
            this.route = msg.route;
            msg.route = route;

            boolean retryEnabled = this.retryEnabled;
            this.retryEnabled = msg.retryEnabled;
            msg.retryEnabled = retryEnabled;

            int retry = this.retry;
            this.retry = msg.retry;
            msg.retry = retry;

            long timeReceived = this.timeReceived;
            this.timeReceived = msg.timeReceived;
            msg.timeReceived = timeReceived;

            long timeRemaining = this.timeRemaining;
            this.timeRemaining = msg.timeRemaining;
            msg.timeRemaining = timeRemaining;
        }
    }

    /**
     * <p>Return the route of this routable.</p>
     *
     * @return The route.
     */
    public Route getRoute() {
        return route;
    }

    /**
     * <p>Set a new route for this routable.</p>
     *
     * @param route The new route.
     * @return This, to allow chaining.
     */
    public Message setRoute(Route route) {
        this.route = new Route(route);
        return this;
    }

    /**
     * <p>Returns the timestamp for when this message was last seen by message bus. If you are using this to determine
     * message expiration, you should use {@link #isExpired()} instead.</p>
     *
     * @return The timestamp this was last seen.
     */
    public long getTimeReceived() {
        return timeReceived;
    }

    /**
     * <p>Sets the timestamp for when this message was last seen by message bus to the given time in milliseconds since
     * epoch. Please see comment on {@link #isExpired()} for more information on how to determine whether or not a
     * message has expired. You should never need to call this method yourself, as it is touched automatically whenever
     * message bus encounters a new message.</p>
     *
     * @param timeReceived The time received in milliseconds.
     * @return This, to allow chaining.
     */
    public Message setTimeReceived(long timeReceived) {
        this.timeReceived = timeReceived;
        return this;
    }

    /**
     * <p>This is a convenience method to call {@link #setTimeReceived(long)} passing the current time as argument.</p>
     *
     * @return This, to allow chaining.
     */
    public Message setTimeReceivedNow() {
        return setTimeReceived(SystemTimer.INSTANCE.milliTime());
    }

    /**
     * <p>Returns the number of milliseconds that remain before this message times out. This value is only updated by
     * the network layer, and is therefore not current. If you are trying to determine message expiration, use {@link
     * #isExpired()} instead.</p>
     *
     * @return The remaining time in milliseconds.
     */
    public long getTimeRemaining() {
        return timeRemaining;
    }

    /**
     * <p>Sets the numer of milliseconds that remain before this message times out. Please see comment on {@link
     * #isExpired()} for more information on how to determine whether or not a message has expired.</p>
     *
     * @param timeRemaining The number of milliseconds until expiration.
     * @return This, to allow chaining.
     */
    public Message setTimeRemaining(long timeRemaining) {
        this.timeRemaining = timeRemaining;
        return this;
    }

    /**
     * <p>Returns the number of milliseconds that remain right now before this message times out. This is a function of
     * {@link #getTimeReceived()}, {@link #getTimeRemaining()} and current time. Whenever a message is transmitted by
     * message bus, a new remaining time is calculated and serialized as <code>timeRemaining = timeRemaining -
     * (currentTime - timeReceived)</code>. This means that we are doing an over-estimate of remaining time, as we are
     * only factoring in the time used by the application above message bus.</p>
     *
     * @return The remaining time in milliseconds.
     */
    public long getTimeRemainingNow() {
        return timeRemaining - (SystemTimer.INSTANCE.milliTime() - timeReceived);
    }

    /**
     * <p>Returns whether or not this message has expired.</p>
     *
     * @return True if {@link #getTimeRemainingNow()} is less than or equal to zero.
     */
    public boolean isExpired() {
        return getTimeRemainingNow() <= 0;
    }

    /**
     * <p>Returns whether or not this message contains a sequence identifier that should be respected, i.e. whether or
     * not this message requires sequencing.</p>
     *
     * @return True to enable sequencing.
     * @see #getSequenceId()
     */
    public boolean hasSequenceId() {
        return false;
    }

    /**
     * <p>Returns the identifier used to order messages. Any two messages that have the same sequence id are ensured to
     * arrive at the recipient in the order they were sent by the client. This value is only respected if the {@link
     * #hasSequenceId()} method returns true.</p>
     *
     * @return The sequence identifier.
     */
    public long getSequenceId() {
        return 0;
    }

    /**
     * <p>Returns whether or not this message contains a sequence bucket that should be respected, i.e. whether or not
     * this message requires bucket-level sequencing.</p>
     *
     * @return True to enable bucket sequencing.
     * @see #getBucketSequence()
     */
    public boolean hasBucketSequence() {
        return false;
    }

    /**
     * <p>Returns the identifier used to order message buckets. Any two messages that have the same bucket sequence are
     * ensured to arrive at the NEXT peer in the order they were sent by THIS peer. This value is only respected if the
     * {@link #hasBucketSequence()} method returns true.</p>
     *
     * @return The bucket sequence.
     */
    public long getBucketSequence() {
        return 0;
    }

    /**
     * <p>Obtain the approximate size of this message object in bytes. This enables messagebus to track the size of the
     * send queue in both memory usage and item count. This method returns 1 by default, and must be overridden to
     * enable message size tracking.</p>
     *
     * @return 1
     */
    public int getApproxSize() {
        return 1;
    }

    /**
     * <p>Sets whether or not this message can be resent.</p>
     *
     * @param enabled Resendable flag.
     */
    public void setRetryEnabled(boolean enabled) {
        retryEnabled = enabled;
    }

    /**
     * <p>Returns whether or not this message can be resent.</p>
     *
     * @return True if this can be resent.
     */
    public boolean getRetryEnabled() {
        return retryEnabled;
    }

    /**
     * <p>Returns the number of times the sending of this message has been retried. This is available for inspection so
     * that clients may implement logic to control resending.</p>
     *
     * @return The retry count.
     * @see Reply#setRetryDelay This method can be used to request resending that differs from the default.
     */
    public int getRetry() {
        return retry;
    }

    /**
     * <p>Sets the number of times the sending of this message has been retried. This method only makes sense to modify
     * BEFORE sending it, since its value is not serialized back into any reply that it may create.</p>
     *
     * @param retry The retry count.
     * @return This, to allow chaining.
     */
    public Message setRetry(int retry) {
        this.retry = retry;
        return this;
    }
}

