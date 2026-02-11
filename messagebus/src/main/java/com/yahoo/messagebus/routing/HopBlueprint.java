// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import java.util.*;

/**
 * A hop blueprint is a stored prototype of a hop that has been created from a {@link HopSpec} object. A map of these
 * are stored in a {@link RoutingTable}.
 *
 * @author bratseth
 * @author Simon Thoresen Hult
 */
public class HopBlueprint {

    private final List<HopDirective> selector = new ArrayList<>();
    private final List<Hop> recipients = new ArrayList<>();
    private boolean ignoreResult = false;

    /**
     * The default constructor requires valid arguments for all member variables.
     *
     * @param spec The spec of this rule.
     */
    HopBlueprint(HopSpec spec) {
        Hop hop = Hop.parse(spec.getSelector());
        for (int i = 0; i < hop.getNumDirectives(); ++i) {
            selector.add(hop.getDirective(i));
        }
        List<String> lst = new ArrayList<>();
        for (int i = 0; i < spec.getNumRecipients(); ++i) {
            lst.add(spec.getRecipient(i));
        }
        for (String recipient : lst) {
            recipients.add(Hop.parse(recipient));
        }
        ignoreResult = spec.getIgnoreResult();
    }

    /**
     * Creates a hop instance from thie blueprint.
     *
     * @return The created hop.
     */
    public Hop create() {
        return new Hop(selector, ignoreResult);
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
     * Returns whether or not there are any recipients that the selector can choose from.
     *
     * @return True if there is at least one recipient.
     */
    public boolean hasRecipients() {
        return !recipients.isEmpty();
    }

    /**
     * Returns the number of recipients that the selector can choose from.
     *
     * @return The number of recipients.
     */
    public int getNumRecipients() {
        return recipients.size();
    }

    /**
     * Returns the recipient at the given index.
     *
     * @param i The index of the recipient to return.
     * @return The recipient at the given index.
     */
    public Hop getRecipient(int i) {
        return recipients.get(i);
    }

    /**
     * Returns whether or not to ignore the result when routing through this hop.
     *
     * @return True to ignore the result.
     */
    public boolean getIgnoreResult() {
        return ignoreResult;
    }

    /**
     * Sets whether or not to ignore the result when routing through this hop.
     *
     * @param ignoreResult Whether or not to ignore the result.
     * @return This, to allow chaining.
     */
    public HopBlueprint setIgnoreResult(boolean ignoreResult) {
        this.ignoreResult = ignoreResult;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("HopBlueprint(selector = { ");
        for (int i = 0; i < selector.size(); ++i) {
            ret.append("'").append(selector.get(i)).append("'");
            if (i < selector.size() - 1) {
                ret.append(", ");
            }
        }
        ret.append(" }, recipients = { ");
        for (int i = 0; i < recipients.size(); ++i) {
            ret.append("'").append(recipients.get(i)).append("'");
            if (i < recipients.size() - 1) {
                ret.append(", ");
            }
        }
        ret.append(" }, ignoreResult = ").append(ignoreResult).append(")");
        return ret.toString();
    }
}

