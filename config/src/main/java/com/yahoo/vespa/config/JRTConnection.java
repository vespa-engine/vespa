// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;

import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A JRT connection to a config server or config proxy.
 *
 * @author Gunnar Gauslaa Bergem
 * @author hmusum
 */
public class JRTConnection implements Connection {
    private final static Logger logger = Logger.getLogger(JRTConnection.class.getName());

    private final String address;
    private final Supervisor supervisor;
    private Target target;

    public JRTConnection(String address, Supervisor supervisor) {
        this.address = address;
        this.supervisor = supervisor;
    }

    @Override
    public void invokeAsync(Request request, Duration jrtTimeout, RequestWaiter requestWaiter) {
        getTarget().invokeAsync(request, jrtTimeout, requestWaiter);
    }

    @Override
    public void invokeSync(Request request, Duration jrtTimeout) {
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
        }
        return target;
    }

    @Override
    public String toString() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JRTConnection that = (JRTConnection) o;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

}
