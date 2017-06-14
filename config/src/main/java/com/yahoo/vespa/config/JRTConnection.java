// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.*;

import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.logging.Logger;

/**
 * A JRT connection to a config server or config proxy.
 *
 * @author <a href="mailto:gunnarga@yahoo-inc.com">Gunnar Gauslaa Bergem</a>
 */
public class JRTConnection implements Connection {

    private final String address;
    private final Supervisor supervisor;
    private Target target;

    private long lastConnectionAttempt = 0;  // Timestamp for last connection attempt
    private long lastSuccess = 0;
    private long lastFailure = 0;

    private static final long delayBetweenConnectionMessage = 30000; //ms

    private static SimpleDateFormat yyyyMMddz;
    static {
        yyyyMMddz = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        yyyyMMddz.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    @Override
    public void invokeAsync(Request request, double jrtTimeout, RequestWaiter requestWaiter) {
        getTarget().invokeAsync(request, jrtTimeout, requestWaiter);
    }

    public final static Logger logger = Logger.getLogger(JRTConnection.class.getPackage().getName());


    public JRTConnection(String address, Supervisor supervisor) {
        this.address = address;
        this.supervisor = supervisor;
    }

    public String getAddress() {
        return address;
    }

    /**
     * This is synchronized to avoid multiple ConfigInstances creating new targets simultaneously, if
     * the existing target is null, invalid or has not yet been initialized.
     *
     * @return The existing target, or a new one if invalid or null.
     */
    public synchronized Target getTarget() {
        if (target == null || !target.isValid()) {
            if ((System.currentTimeMillis() - lastConnectionAttempt) > delayBetweenConnectionMessage) {
                logger.fine("Connecting to " + address);
            }
            lastConnectionAttempt = System.currentTimeMillis();
            target = supervisor.connect(new Spec(address));
        }
        return target;
    }

    @Override
    public synchronized void setError(int errorCode) {
        lastFailure = System.currentTimeMillis();
    }

    @Override
    public synchronized void setSuccess() {
        lastSuccess = System.currentTimeMillis();
    }

    public void setLastSuccess() {
        lastSuccess = System.currentTimeMillis();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Address: ");
        sb.append(address);
        if (lastSuccess > 0) {
            sb.append("\n");
            sb.append("Last success: ");
            sb.append(yyyyMMddz.format(lastSuccess));
        }
        if (lastFailure > 0) {
            sb.append("\n");
            sb.append("Last failure: ");
            sb.append(yyyyMMddz.format(lastFailure));
        }
        return sb.toString();
    }
}
