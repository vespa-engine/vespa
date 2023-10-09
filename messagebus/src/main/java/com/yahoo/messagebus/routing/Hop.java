// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Hops are the components of routes.
 * They are instantiated from a {@link HopBlueprint} or
 * using the factory method {@link #parse(String)}. A hop is resolved to a recipient, from
 * a set of primitives, either a string primitive that is to be matched verbatim to a
 * service address, or a {@link RoutingPolicy} directive.</p>
 *
 * @author bratseth
 */
public class Hop {

    private final List<HopDirective> selector = new ArrayList<>();
    private boolean ignoreResult = false;
    private String cache = null;

    /**
     * Constructs an empty hop. You will need to add directives to the
     * selector to make this usable.
     */
    public Hop() {
        // empty
    }

    /**
     * Implements the copy constructor.
     *
     * @param hop The hop to copy.
     */
    public Hop(Hop hop) {
        selector.addAll(hop.selector);
        ignoreResult = hop.ignoreResult;
    }

    /**
     * Constructs a fully populated hop. This is package private and used by
     * the {@link HopBlueprint#create()} method.
     *
     * @param selector     The selector to copy.
     * @param ignoreResult Whether or not to ignore the result of this hop.
     */
    Hop(List<HopDirective> selector, boolean ignoreResult) {
        this.selector.addAll(selector);
        this.ignoreResult = ignoreResult;
    }

    /**
     * Parses the given string as a single hop. The {@link #toString()}
     * method is compatible with this parser.
     *
     * @param str The string to parse.
     * @return A hop that corresponds to the string.
     */
    public static Hop parse(String str) {
        Route route = Route.parse(str);
        if (route.getNumHops() > 1) {
            return new Hop().addDirective(new ErrorDirective("Failed to completely parse '" + str + "'."));
        }
        return route.getHop(0);
    }

    /**
     * Returns whether or not there are any directives contained in this hop.
     *
     * @return True if there is at least one directive.
     */
    public boolean hasDirectives() {
        return !selector.isEmpty();
    }

    /**
     * Returns the number of directives contained in this hop.
     *
     * @return The number of directives.
     */
    public int getNumDirectives() {
        return selector.size();
    }

    /**
     * Returns the directive at the given index.
     *
     * @param i The index of the directive to return.
     * @return The item.
     */
    public HopDirective getDirective(int i) {
        return selector.get(i);
    }

    /**
     * Adds a new directive to this hop.
     *
     * @param directive The directive to add.
     * @return This, to allow chaining.
     */
    public Hop addDirective(HopDirective directive) {
        cache = null;
        selector.add(directive);
        return this;
    }

    /**
     * Sets the directive at a given index.
     *
     * @param i         The index at which to set the directive.
     * @param directive The directive to set.
     * @return This, to allow chaining.
     */
    public Hop setDirective(int i, HopDirective directive) {
        selector.set(i, directive);
        return this;
    }

    /**
     * <p>Removes the directive at the given index.</p>
     *
     * @param i The index of the directive to remove.
     * @return The removed directive.
     */
    public HopDirective removeDirective(int i) {
        cache = null;
        return selector.remove(i);
    }

    /**
     * <p>Clears all directives from this hop.</p>
     *
     * @return This, to allow chaining.
     */
    public Hop clearDirectives() {
        cache = null;
        selector.clear();
        return this;
    }

    /**
     * <p>Returns whether or not to ignore the result when routing through this
     * hop.</p>
     *
     * @return True to ignore the result.
     */
    public boolean getIgnoreResult() {
        return ignoreResult;
    }

    /**
     * <p>Sets whether or not to ignore the result when routing through this
     * hop.</p>
     *
     * @param ignoreResult Whether or not to ignore the result.
     * @return This, to allow chaining.
     */
    public Hop setIgnoreResult(boolean ignoreResult) {
        this.ignoreResult = ignoreResult;
        return this;
    }

    /**
     * <p>Returns true whether this hop matches another. This respects policy
     * directives matching any other.</p>
     *
     * @param hop The hop to compare to.
     * @return True if this matches the argument, false otherwise.
     */
    public boolean matches(Hop hop) {
        if (hop == null || hop.getNumDirectives() != selector.size()) {
            return false;
        }
        for (int i = 0; i < hop.getNumDirectives(); ++i) {
            if (!selector.get(i).matches(hop.getDirective(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>Returns a string representation of this that can be debugged but not
     * parsed.</p>
     *
     * @return The debug string.
     */
    public String toDebugString() {
        StringBuilder ret = new StringBuilder("Hop(selector = { ");
        for (int i = 0; i < selector.size(); ++i) {
            ret.append(selector.get(i).toDebugString());
            if (i < selector.size() - 1) {
                ret.append(", ");
            }
        }
        ret.append(" }, ignoreResult = ").append(ignoreResult).append(")");
        return ret.toString();
    }

    @Override
    public String toString() {
        return (ignoreResult ? "?" : "") + getServiceName();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Hop)) {
            return false;
        }
        Hop rhs = (Hop)obj;
        if (selector.size() != rhs.selector.size()) {
            return false;
        }
        for (int i = 0; i < selector.size(); ++i) {
            if (!selector.get(i).equals(rhs.selector.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>Returns the service name referenced by this hop. This is the
     * concatenation of all selector primitives, but with no ignore-result
     * prefix.</p>
     *
     * @return The service name.
     */
    public String getServiceName() {
        if (cache == null) {
            cache = toString(0, selector.size());
        }
        return cache;
    }

    /**
     * <p>Returns a string concatenation of a subset of the selector primitives
     * contained in this.</p>
     *
     * @param fromIncluding  The index of the first primitive to include.
     * @param toNotIncluding The index after the last primitive to include.
     * @return The string concatenation.
     */
    public String toString(int fromIncluding, int toNotIncluding) {
        StringBuilder ret = new StringBuilder();
        for (int i = fromIncluding; i < toNotIncluding; ++i) {
            ret.append(selector.get(i));
            if (i < toNotIncluding - 1) {
                ret.append("/");
            }
        }
        return ret.toString();
    }

    /**
     * <p>Returns the prefix of this hop's selector to, but not including, the
     * given index.</p>
     *
     * @param toNotIncluding The index to which to generate prefix.
     * @return The prefix before the index.
     */
    public String getPrefix(int toNotIncluding) {
        if (toNotIncluding > 0) {
            return toString(0, toNotIncluding) + "/";
        }
        return "";
    }

    /**
     * <p>Returns the suffix of this hop's selector from, but not including, the
     * given index.</p>
     *
     * @param fromNotIncluding The index from which to generate suffix.
     * @return The suffix after the index.
     */
    public String getSuffix(int fromNotIncluding) {
        if (fromNotIncluding < selector.size() - 1) {
            return "/" + toString(fromNotIncluding + 1, selector.size());
        }
        return "";
    }

    @Override
    public int hashCode() {
        int result = selector.hashCode();
        result = 31 * result + (ignoreResult ? 1 : 0);
        result = 31 * result + (cache != null ? cache.hashCode() : 0);
        return result;
    }

}
