// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * An immutable compound name of the general form "a.bb.ccc",
 * where there can be any number of such compounds, including one or zero.
 * <p>
 * Using CompoundName is generally substantially faster than using strings.
 *
 * @author bratseth
 */
public final class CompoundName {

    /**
     * The string name of this compound.
     */
    private final String name;
    private final String lowerCasedName;

    private final List<String> compounds;

    /** A hashcode which is always derived from the compounds (NEVER the string) */
    private final int hashCode;

    /** This name with the first component removed */
    private final CompoundName rest;

    /** The empty compound */
    public static final CompoundName empty = new CompoundName("");

    /**
     * Constructs this from a string which may contains dot-separated components
     *
     * @throws NullPointerException if name is null
     */
    public CompoundName(String name) {
        this(name, parse(name).toArray(new String[1]));
    }

    /** Constructs this from an array of name components which are assumed not to contain dots */
    public static CompoundName fromComponents(String ... components) {
        return new CompoundName(List.of(components));
    }

    /** Constructs this from a list of compounds. */
    public CompoundName(List<String> compounds) {
        this(compounds.toArray(new String[compounds.size()]));
    }

    private CompoundName(String [] compounds) {
        this(toCompoundString(compounds), compounds);
    }

    /**
     * Constructs this from a name with already parsed compounds.
     * Private to avoid creating names with inconsistencies.
     *
     * @param name the string representation of the compounds
     * @param compounds the compounds of this name
     */
    private CompoundName(String name, String [] compounds) {
        if (name == null) throw new NullPointerException("Name can not be null");

        this.name = name;
        this.lowerCasedName = toLowerCase(name);
        if (compounds.length == 1 && compounds[0].isEmpty()) {
            this.compounds = List.of();
            this.hashCode = 0;
            rest = this;
            return;
        }
        this.compounds = new ImmutableArrayList(compounds);
        this.hashCode = this.compounds.hashCode();

        rest = compounds.length > 1 ? new CompoundName(name.substring(compounds[0].length()+1), Arrays.copyOfRange(compounds, 1, compounds.length))
                        : empty;
    }

    private static List<String> parse(String s) {
        ArrayList<String> l = null;
        int p = 0;
        final int m = s.length();
        for (int i = 0; i < m; i++) {
            if (s.charAt(i) == '.') {
                if (l == null) l = new ArrayList<>(8);
                l.add(s.substring(p, i));
                p = i + 1;
            }
        }
        if (p == 0) {
            if (l == null) return List.of(s);
            l.add(s);
        } else if (p < m) {
            l.add(s.substring(p, m));
        } else {
            throw new IllegalArgumentException("'" + s + "' is not a legal compound name. Names can not end with a dot.");
        }
        return l;
    }

    /**
     * Returns a compound name which has the given compound string appended to it
     *
     * @param name if name is empty this returns <code>this</code>
     */
    public CompoundName append(String name) {
        if (name.isEmpty()) return this;
        return append(new CompoundName(name));
    }

    /**
     * Returns a compound name which has the given compounds appended to it
     *
     * @param name if name is empty this returns <code>this</code>
     */
    public CompoundName append(CompoundName name) {
        if (name.isEmpty()) return this;
        if (isEmpty()) return name;
        String [] newCompounds = new String[compounds.size() + name.compounds.size()];
        int count = 0;
        for (String s : compounds) { newCompounds[count++] = s; }
        for (String s : name.compounds) { newCompounds[count++] = s; }
        return new CompoundName(concat(this.name, name.name), newCompounds);
    }

    private static String concat(String name1, String name2) {
        return name1 + "." + name2;
    }

    /**
     * Returns a compound name which has the given name components prepended to this name,
     * in the given order, i.e new ComponentName("c").prepend("a","b") will yield "a.b.c".
     *
     * @param nameParts if name is empty this returns <code>this</code>
     */
    public CompoundName prepend(String ... nameParts) {
        if (nameParts.length == 0) return this;
        if (isEmpty()) return fromComponents(nameParts);

        List<String> newCompounds = new ArrayList<>(nameParts.length + compounds.size());
        newCompounds.addAll(Arrays.asList(nameParts));
        newCompounds.addAll(this.compounds);
        return new CompoundName(newCompounds);
    }

    /**
     * Returns the name after the last dot. If there are no dots, the full name is returned.
     */
    public String last() {
        if (compounds.isEmpty()) return "";
        return compounds.get(compounds.size() - 1);
    }

    /**
     * Returns the name before the first dot. If there are no dots the full name is returned.
     */
    public String first() {
        if (compounds.isEmpty()) return "";
        return compounds.get(0);
    }

    /**
     * Returns the first n components of this.
     *
     * @throws IllegalArgumentException if this does not have at least n components
     */
    public CompoundName first(int n) {
        if (compounds.size() < n)
            throw new IllegalArgumentException("Asked for the first " + n + " components but '" +
                                               this + "' only have " + compounds.size() + " components.");
        if (compounds.size() == n) return this;
        if (compounds.size() == 0) return empty;
        return new CompoundName(compounds.subList(0, n));
    }

    /**
     * Returns the name after the first dot, or "" if this name has no dots
     */
    public CompoundName rest() { return rest; }

    /**
     * Returns the name starting after the n first components (i.e dots).
     * This may be the empty name.
     *
     * @throws IllegalArgumentException if this does not have at least that many components
     */
    public CompoundName rest(int n) {
        if (n == 0) return this;
        if (compounds.size() < n)
            throw new IllegalArgumentException("Asked for the rest after " + n + " components but '" +
                                               this + "' only have " + compounds.size() + " components.");
        if (n == 1) return rest();
        if (compounds.size() == n) return empty;
        return rest.rest(n - 1);
    }

    /**
     * Returns the number of compound elements in this. Which is exactly the number of dots in the string plus one.
     * The size of an empty compound is 0.
     */
    public int size() {
        return compounds.size();
    }

    /**
     * Returns the compound element as the given index
     */
    public String get(int i) {
        return compounds.get(i);
    }

    /**
     * Returns a compound which have the name component at index i set to the given name.
     * As an optimization, if the given name == the name component at this index, this is returned.
     */
    public CompoundName set(int i, String name) {
        if (get(i).equals(name)) return this;
        List<String> newCompounds = new ArrayList<>(compounds);
        newCompounds.set(i, name);
        return new CompoundName(newCompounds);
    }

    /**
     * Returns whether this name has more than one element
     */
    public boolean isCompound() {
        return compounds.size() > 1;
    }

    public boolean isEmpty() {
        return compounds.isEmpty();
    }

    /**
     * Returns whether the given name is a prefix of this.
     * Prefixes are taken on the component, not character level, so
     * "a" is a prefix of "a.b", but not a prefix of "ax.b
     */
    public boolean hasPrefix(CompoundName prefix) {
        if (prefix.size() > this.size()) return false;

        int prefixLength = prefix.name.length();
        if (prefixLength == 0)
            return true;

        if (name.length() > prefixLength && name.charAt(prefixLength) != '.')
            return false;

        return name.startsWith(prefix.name);
    }

    /**
     * Returns an immutable list of the components of this
     */
    public List<String> asList() {
        return compounds;
    }

    @Override
    public int hashCode() { return hashCode; }

    @Override
    public boolean equals(Object arg) {
        if (arg == this) return true;
        return (arg instanceof CompoundName o) && name.equals(o.name);
    }

    /**
     * Returns the string representation of this - all the name components in order separated by dots.
     */
    @Override
    public String toString() { return name; }

    public String getLowerCasedName() {
        return lowerCasedName;
    }

    private static String toCompoundString(String [] compounds) {
        int all = compounds.length;
        for (int i = 0; i < compounds.length; i++)
            all += compounds[i].length();
        StringBuilder b = new StringBuilder(all);
        for (int i = 0; i < compounds.length; i++)
            b.append(compounds[i]).append(".");
        return b.length()==0 ? "" : b.substring(0, b.length()-1);
    }

    public static CompoundName from(String name) { return new CompoundName(name); }

    private static class ImmutableArrayList extends AbstractList<String> {

        private final String [] array;
        ImmutableArrayList(String [] array) {
            this.array = array;
        }
        @Override
        public String get(int index) {
            return array[index];
        }

        @Override
        public int size() {
            return array.length;
        }

        @Override
        public int hashCode() {
            int hashCode = 0;
            for (String s : array) {
                hashCode = hashCode ^ s.hashCode();
            }
            return hashCode;
        }
    }

}
