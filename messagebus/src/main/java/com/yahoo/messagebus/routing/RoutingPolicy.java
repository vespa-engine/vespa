// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * Decides how to choose between candidate recipients of a hop template point.
 * <p>
 * The routing policy will be given an address and a set of recipients matching this address. It is responsible for
 * choosing one of the given recipients each time {@link #select} is called. The routing policy chooses which recipient
 * to return each time at its sole discretion, using information in the RoutingContext argument to choose or not as required
 * to implement the policy.
 * <p>
 * Example:
 * <ul>
 *     <li>The given <i>address</i> is <code>a/b/?</code>
 *     <li>The given <i>recipients</i> are <code>a/b/c, a/b/d and a/b/e</code> - one of these three must be returned on every call to <code>choose</code>
 * </ul>
 * <p>
 * This class is pluggable per template point in the address of a hop.
 *
 * @author bratseth
 * @author Simon Thoresen Hult
 */
public interface RoutingPolicy {

    /**
     * This function must choose a set of services that is to receive the given message from a list of possible
     * recipients. This is done by adding child routing contexts to the argument object. These children can then be
     * iterated and manipulated even before selection pass is concluded.
     *
     * @param context the complete context for the invocation of this policy. Contains all available data.
     */
    void select(RoutingContext context);

    /**
     * This function is called when all replies have arrived for some message. The implementation is responsible for
     * merging multiple replies into a single sensible reply. The replies is contained in the child context objects of
     * the argument context, and then response must be set in that context.
     *
     * @param context the complete context for the invocation of this policy. Contains all available data.
     */
    void merge(RoutingContext context);

    /**
     * Destroys this factory and frees up any resources it has held. Making further calls on a destroyed
     * factory causes a runtime exception.
     */
    void destroy();

}
