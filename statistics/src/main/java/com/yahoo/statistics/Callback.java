// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

/**
 * Definition of the callback interface for the statistics API. It is a common
 * use case to need a reference to the Handle (e.g. Value or Counter) which a
 * callback is related to. Since everything in a Handle since 5.1.4 is fully
 * initialized from the constructor, it became cumbersome to use Runnable for
 * the callback and this interface came into use.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @since 5.1.4
 */
public interface Callback {
    /**
     * Invoked each logging cycle right before the events for a Handle are
     * emitted to the log.
     *
     * @param h
     *            the handle which invoked this callback
     * @param firstTime
     *            true the first time the method is invoked from h, false later
     */
    public void run(Handle h, boolean firstTime);
}
