// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.net.control;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class is used to represent the state of each log level
 * in a set of states.
 *
 * @author  Bjorn Borud
 */
public class Levels implements Cloneable {
    private final Map<String, State> levelsMap = new LinkedHashMap<String, State>(10);

    /**
     * The constructor initializes the Levels object to its default
     * state.
     */
    public Levels () {
        levelsMap.put("event", State.FORWARD);
        levelsMap.put("fatal", State.FORWARD);
        levelsMap.put("error", State.FORWARD);
        levelsMap.put("warning", State.FORWARD);
        levelsMap.put("info", State.FORWARD);
        levelsMap.put("config", State.FORWARD);
        levelsMap.put("debug", State.NOFORWARD);
        levelsMap.put("spam", State.NOFORWARD);
    }

    /**
     * Parse a levels representation and return a Levels object
     * representing the levels.
     *
     * @param levels A string representation of the levels
     * @return new instance of Levels, possibly having no
     *         real values if none could be found in the
     *         <code>levels</code> parameter.
     *
     */
    public static Levels parse (String levels) {
        return (new Levels()).updateLevels(levels);
    }


    /**
     * Update the levels given a string representation of the state;
     * the levels mentioned here will be updated, the ones omitted
     * will retain their state as before the function call.
     *
     * @param levels string representation of levels
     *
     */
    public Levels updateLevels (String levels) {
        String[] parts = levels.split(",");
        if (parts.length < 1) {
            return this;
        }

        for (int i = 0; i < parts.length; i++) {
            String pair = parts[i];
            int offset = pair.indexOf('=');
            if (offset != -1) {
                String name  = pair.substring(0,offset).trim().toLowerCase();
                String value = pair.substring(offset+1).trim().toLowerCase();
                setLevelState(name, State.parse(value));
            }
        }
        return this;
    }


    /**
     * Set the state of a given level.
     *
     * @param level name of the level
     * @param state the state
     * @return returns reference to <code>this</code> for chaning
     */
    public Levels setLevelState(String level, State state) {
        levelsMap.put(level, state);
        return this;
    }

    /**
     * Get the state of a given level.
     *
     */
    public State getLevelState (String level) {
        State s = levelsMap.get(level);
        if (s == null) {
            return State.UNKNOWN;
        }
        return s;
    }

    /**
     * For equivalent configurations the toString method should
     * emit equal strings.
     *
     */
    public String toString () {
        StringBuilder sbuf = new StringBuilder(80);
        boolean first = true;
        for (Map.Entry<String, State> me : levelsMap.entrySet()) {
            // commas between
        	if (!first) {
        		sbuf.append(',');
        	} else {
        		first = false;
        	}
            sbuf.append(me.getKey())
                .append('=')
                .append(me.getValue());
        }

        return sbuf.toString();
    }

    public Object clone() {
        // quick and dirty, but easily verifiable to be correct
        return parse(this.toString());
    }

}
