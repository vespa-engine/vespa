// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.io.Serializable;

/**
 * A search-time document attribute sort specification(per-document in-memory value).
 * This belongs to the attribute or field(implicitt attribute).
 *
 * @author baldersheim
 */
public final class Sorting implements Cloneable, Serializable {

    // Remember to change hashCode and equals when you add new fields
    public enum Function {UCA, RAW, LOWERCASE}
    public enum Strength {PRIMARY, SECONDARY, TERTIARY, QUATERNARY, IDENTICAL}
    private boolean ascending = true;
    private Function function = Function.UCA;
    private String locale = "";
    private Strength strength = Strength.PRIMARY;

    public boolean isAscending()       { return ascending; }
    public boolean isDescending()      { return ! ascending; }
    public String getLocale()          { return locale; }
    public Function getFunction()      { return function; }
    public Strength getStrength()      { return strength; }

    public void setAscending()                 { ascending = true; }
    public void setDescending()                { ascending = false; }
    public void setFunction(Function function) { this.function = function; }
    public void setLocale(String locale)       { this.locale = locale; }
    public void setStrength(Strength strength) { this.strength = strength; }

    public int hashCode() {
        return locale.hashCode() +
               strength.hashCode() +
               function.hashCode() +
               (isDescending() ? 13 : 0);
    }

    public boolean equals(Object object) {
        if (! (object instanceof Sorting)) return false;

        Sorting other=(Sorting)object;
        return this.locale.equals(other.locale) &&
               (ascending == other.ascending) &&
               (function == other.function) &&
               (strength == other.strength);
    }

    @Override
    public Sorting clone() {
        try {
            return (Sorting)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error");
        }
    }

    public String toString() {
        return "sorting '" + (isAscending() ? '+' : '-') + function.toString() + "(" + strength.toString() + ", " + locale + ")";
    }

}
