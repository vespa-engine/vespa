// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A JRT connection to a config server or config proxy.
 *
 * @author Gunnar Gauslaa Bergem
 * @author hmusum
 */
public class JRTConnection implements Connection {
    private final static Logger logger = Logger.getLogger(JRTConnection.class.getPackage().getName());

    private final String address;
    private final Supervisor supervisor;
    private Target target;

    private Instant lastConnected = Instant.EPOCH.plus(Duration.ofSeconds(1)); // to be healthy initially, see isHealthy()
    private Instant lastSuccess = Instant.EPOCH;
    private Instant lastFailure = Instant.EPOCH;

    public JRTConnection(String address, Supervisor supervisor) {
        this.address = address;
        this.supervisor = supervisor;
    }

    @Override
    public void invokeAsync(Request request, double jrtTimeout, RequestWaiter requestWaiter) {
        getTarget().invokeAsync(request, jrtTimeout, requestWaiter);
    }

    @Override
    public void invokeSync(Request request, double jrtTimeout) {
        getTarget().invokeSync(request, jrtTimeout);
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
            logger.log(Level.INFO, "Connecting to " + address);
            target = supervisor.connect(new Spec(address));
            lastConnected = Instant.now();
        }
        return target;
    }

    @Override
    public synchronized void setError(int errorCode) {
        lastFailure = Instant.now();
    }

    @Override
    public synchronized void setSuccess() {
        lastSuccess = Instant.now();
    }

    public synchronized boolean isHealthy() {
        return lastSuccess.isAfter(lastFailure) || lastConnected.isAfter(lastFailure);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Address: ");
        sb.append(address);
        sb.append("\n").append("Healthy: ").append(isHealthy());
        if (lastSuccess.isAfter(Instant.EPOCH)) {
            sb.append("\n");
            sb.append("Last success: ");
            sb.append(lastSuccess);
        }
        if (lastFailure.isAfter(Instant.EPOCH)) {
            sb.append("\n");
            sb.append("Last failure: ");
            sb.append(lastFailure);
        }
        return sb.toString();
    }

}
