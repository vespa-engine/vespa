// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.model.federation;

import java.util.Objects;

/**
 * Options for controlling federation to a single source.
 * This is a value object.
 *
 * @author Tony Vaagenes
 */
public class FederationOptions implements Cloneable {

    private final Boolean optional;
    private final Integer timeoutInMilliseconds;
    private final Integer requestTimeoutInMilliseconds;
    private final Boolean useByDefault;

    /**
     * Creates a request with no separate requestTimeoutInMilliseconds
     */
    public FederationOptions(Boolean optional, Integer timeoutInMilliseconds, Boolean useByDefault) {
        this(optional, timeoutInMilliseconds, null, useByDefault);
    }

    /**
     * Creates a fully specified set of options
     *
     * @param optional whether this should be optional
     * @param timeoutInMilliseconds the max time to wait for a result from this source, or null to use the timeout of the query
     * @param requestTimeoutInMilliseconds the max time to allow this request to live, or null to make this the same as
     *                                     timeoutInMilliseconds. Setting this higher than timeoutInMilliseconds is
     *                                     useful to use queries to populate the cache of slow sources
     * @param useByDefault whether this should be invoked by default
     */
    public FederationOptions(Boolean optional, Integer timeoutInMilliseconds, Integer requestTimeoutInMilliseconds, Boolean useByDefault) {
        this.optional = optional;
        this.timeoutInMilliseconds = timeoutInMilliseconds;
        this.requestTimeoutInMilliseconds = requestTimeoutInMilliseconds;
        this.useByDefault = useByDefault;
    }

    /** Creates a set of default options: Mandatory, no timeout restriction and not used by default */
    public FederationOptions() {
        this(null, null, null, null);
    }

    /** Returns a set of options which are the same of this but with optional set to the given value */
    public FederationOptions setOptional(Boolean newOptional) {
        return new FederationOptions(newOptional, timeoutInMilliseconds, requestTimeoutInMilliseconds, useByDefault);
    }

    /** Returns a set of options which are the same of this but with timeout set to the given value */
    public FederationOptions setTimeoutInMilliseconds(Integer newTimeoutInMilliseconds) {
        return new FederationOptions(optional, newTimeoutInMilliseconds, requestTimeoutInMilliseconds, useByDefault);
    }

    /** Returns a set of options which are the same of this but with request timeout set to the given value */
    public FederationOptions setRequestTimeoutInMilliseconds(Integer newRequestTimeoutInMilliseconds) {
        return new FederationOptions(optional, timeoutInMilliseconds, newRequestTimeoutInMilliseconds, useByDefault);
    }

    /** Returns a set of options which are the same of this but with default set to the given value */
    public FederationOptions setUseByDefault(Boolean newUseByDefault) {
        return new FederationOptions(optional, timeoutInMilliseconds, requestTimeoutInMilliseconds, newUseByDefault);
    }

    public boolean getOptional() {
        return (optional != null) ? optional : false;
    }

    /** Returns the amount of time we should wait for this target, or -1 to use default */
    public int getTimeoutInMilliseconds() {
        return (timeoutInMilliseconds != null) ? timeoutInMilliseconds : -1;
    }

    /** Returns the amount of time we should allow this target execution to run, or -1 to use default */
    public int getRequestTimeoutInMilliseconds() {
        return (requestTimeoutInMilliseconds != null) ? requestTimeoutInMilliseconds : -1;
    }

    public long getSearchChainExecutionTimeoutInMilliseconds(long queryTimeout) {
        return getTimeoutInMilliseconds() >= 0 ? getTimeoutInMilliseconds() : queryTimeout;
    }

    public boolean getUseByDefault() {
        return useByDefault != null ? useByDefault : false;
    }

    public FederationOptions inherit(FederationOptions parent) {
        return new FederationOptions(
                inherit(optional, parent.optional),
                inherit(timeoutInMilliseconds, parent.timeoutInMilliseconds),
                inherit(requestTimeoutInMilliseconds, parent.requestTimeoutInMilliseconds),
                inherit(useByDefault, parent.useByDefault));
    }

    private static <T> T inherit(T child, T parent) {
        return (child != null) ? child : parent;
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof FederationOptions) && equals((FederationOptions) other);
    }
    
    public boolean equals(FederationOptions other) {
        return getOptional() == other.getOptional() &&
               getTimeoutInMilliseconds() == other.getTimeoutInMilliseconds();
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getOptional(), getTimeoutInMilliseconds());
    }

    @Override
    public String toString() {
        return "FederationOptions{" +
                "optional=" + optional +
                ", timeoutInMilliseconds=" + timeoutInMilliseconds +
                ", useByDefault=" + useByDefault +
                '}';
    }
}
