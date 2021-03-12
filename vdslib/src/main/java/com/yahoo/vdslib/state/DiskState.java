// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import com.yahoo.text.StringUtilities;

import java.util.StringTokenizer;
import java.text.ParseException;

/**
 *
 */
public class DiskState implements Cloneable {
    private State state = State.UP;
    private String description = "";
    private double capacity = 1.0;

    public DiskState() {}
    public DiskState(State s) {
        setState(s);
    }
    public DiskState(State s, String description, double capacity) {
        setState(s); // Set via set methods, so we can have illegal argument checks only one place
        setCapacity(capacity);
        setDescription(description);
    }
    public DiskState clone() {
        try{
            return (DiskState) super.clone();
        } catch (CloneNotSupportedException e) {
            assert(false); // Should not happen
            return null;
        }
    }

    public DiskState(String serialized) throws ParseException {
        StringTokenizer st = new StringTokenizer(serialized, " \t\f\r\n");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            int index = token.indexOf(':');
            if (index < 0) {
                throw new ParseException("Token " + token + " does not contain ':': " + serialized, 0);
            }
            String key = token.substring(0, index);
            String value = token.substring(index + 1);
            if (key.length() > 0) switch (key.charAt(0)) {
                case 's':
                    if (key.length() > 1) break;
                    setState(State.get(value));
                    continue;
                case 'c':
                    if (key.length() > 1) break;
                    try{
                        setCapacity(Double.valueOf(value));
                    } catch (Exception e) {
                        throw new ParseException("Illegal disk capacity '" + value + "'. Capacity must be a positive floating point number", 0);
                    }
                    continue;
                case 'm':
                    if (key.length() > 1) break;
                    description = StringUtilities.unescape(value);
                    continue;
                default:
                    break;
            }
            // Ignore unknown tokens
        }
    }

    public String serialize(String prefix, boolean includeDescription) {
        boolean empty = true;
        StringBuilder sb = new StringBuilder();
        if (!state.equals(State.UP) || prefix.length() < 2) {
            sb.append(prefix).append("s:").append(state.serialize());
            empty = false;
        }
        if (Math.abs(capacity - 1.0) > 0.000000001) {
            if (empty) { empty = false; } else { sb.append(' '); }
            sb.append(prefix).append("c:").append(capacity);
        }
        if (includeDescription && description.length() > 0) {
            if (!empty) { sb.append(' '); }
            sb.append(prefix).append("m:").append(StringUtilities.escape(description, ' '));
        }
        return sb.toString();
    }

    public State getState() { return state; }
    public double getCapacity() { return capacity; }
    public String getDescription() { return description; }

    public void setState(State s) {
        if (!s.validDiskState()) {
            throw new IllegalArgumentException("State " + s + " is not a valid disk state.");
        }
        state = s;
    }
    public void setCapacity(double capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Negative capacity makes no sense.");
        }
        this.capacity = capacity;
    }
    public void setDescription(String desc) { description = desc; }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DiskState(").append(state.serialize());
        if (Math.abs(capacity - 1.0) > 0.00000001) {
            sb.append(", capacity ").append(capacity);
        }
        if (description.length() > 0) {
            sb.append(": ").append(description);
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DiskState)) { return false; }
        DiskState other = (DiskState) o;
        if (state.equals(other.state)
            && Math.abs(capacity - other.capacity) < 0.00000001)
        {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        // NOTE: capacity cannot be part of the hashCode
        return state.hashCode();
    }
}
