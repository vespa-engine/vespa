// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import java.net.URI;
import java.util.Objects;

/**
 * <p>This class holds the result of a {@link BindingSet#match(URI)} operation. It contains methods to inspect the
 * groups captured during matching, where a <em>group</em> is defined as a sequence of characters matches by a wildcard
 * in the {@link UriPattern}, and to retrieve the matched target.</p>
 *
 * @param <T> The class of the target.
 */
public class BindingMatch<T> {

    private final UriPattern.Match match;
    private final T target;
    private final UriPattern matched;

    /**
     * <p>Constructs a new instance of this class.</p>
     *
     * @param match  The match information for this instance.
     * @param target The target of this match.
     * @param matched The matched URI pattern
     * @throws NullPointerException If any argument is null.
     */
    public BindingMatch(UriPattern.Match match, T target, UriPattern matched) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(target, "target");
        this.match = match;
        this.target = target;
        this.matched = matched;
    }

    /**
     * <p>Returns the number of captured groups of this match. Any non-negative integer smaller than the value returned
     * by this method is a valid group index for this match.</p>
     *
     * @return The number of captured groups.
     */
    public int groupCount() {
        return match.groupCount();
    }

    /**
     * <p>Returns the input subsequence captured by the given group by this match. Groups are indexed from left to
     * right, starting at zero. Note that some groups may match an empty string, in which case this method returns the
     * empty string. This method never returns null.</p>
     *
     * @param idx The index of the group to return.
     * @return The (possibly empty) substring captured by the group during matching, never <code>null</code>.
     * @throws IndexOutOfBoundsException If there is no group in the match with the given index.
     */
    public String group(int idx) {
        return match.group(idx);
    }

    /**
     * <p>Returns the matched target.</p>
     *
     * @return The matched target.
     */
    public T target() {
        return target;
    }

    /**
     * <p>Returns the URI pattern that was matched.</p>
     *
     * @return The matched pattern.
     */
    public UriPattern matched() {
        return matched;
    }

}
