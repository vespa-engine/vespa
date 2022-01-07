// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* -*- c-basic-offset: 4 -*-
 *
 * $Id$
 *
 */
package com.yahoo.io;


import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * What to do if a fatal condition happens in an IO component.
 *
 * <P>
 * TODO: We need to re-think this design a bit.  First off, we
 *       probably need to make the interface an abstract class
 *       or a pure interface type.  Second we provide a few
 *       default implementations which are named after what policy
 *       they implement -- like SystemExitOnError etc.  Also,
 *       runnables that have fatal error handling capability should
 *       probably implement a standard interface for get/set etc.
 *       Also, we should encourage application authors to provide
 *       their own, application specific error handlers rather than
 *       relying on the default.
 *
 *  @author Steinar Knutsen
 */
public class FatalErrorHandler {

    protected static final Logger log = Logger.getLogger(FatalErrorHandler.class.getName());

    /**
     * Do something reasonable when a an Error occurs.
     *
     * Override this to change behavior. Default behavior is to log
     * the error, then exit.
     *
     * @param t The Throwable causing the handler to be activated.
     * @param context The object calling the handler.
     */
    public void handle(Throwable t, Object context) {
        try {
            log.log(Level.SEVERE, "Exiting due to error", t);
        } finally {
            Runtime.getRuntime().halt(1);
        }
    }

}
