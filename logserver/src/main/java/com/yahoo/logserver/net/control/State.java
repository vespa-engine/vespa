// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.net.control;

import java.util.Map;
import java.util.HashMap;

/**
 * This value class represents the logging state of a component.
 * the valid states are:
 *
 * <UL>
 *  <LI> forward - store locally and send to log server
 *  <LI> store - store locally only
 *  <LI> noforward - do not send to logserver
 *  <LI> off - do not generate the message in the program
 * </UL>
 *
 * XXX This does not appear to be in use.
 */
public class State {
    private static final Map<String, State> nameToState = new HashMap<String, State>();

    public static final State FORWARD = new State("forward");
    public static final State NOFORWARD = new State("noforward");
    // public static final State STORE = new State("store");
    // public static final State OFF = new State("off");
    public static final State UNKNOWN = new State("unknown");

    private String name;

    /**
     * Typesafe enum.  Only able to instantiate self.
     * TODO: Rewrite to enum
     */
    private State () {}

    /**
     * Creates state with given name
     */
    private State (String name) {
        this.name = name;
        synchronized (State.class) {
            nameToState.put(name, this);
        }
    }

    public static State parse (String s) {
    	return nameToState.containsKey(s) ? nameToState.get(s) : UNKNOWN;
    }

    public String toString () {
        return name;
    }
}
