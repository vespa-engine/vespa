// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * This class represents a policy directive within a {@link Hop}'s selector. This means to create the named protocol
 * using the given parameter string, and then running that protocol within the context of this directive.
 *
 * @author Simon Thoresen Hult
 */
public class PolicyDirective implements HopDirective {

    private final String name;
    private final String param;

    /**
     * Constructs a new policy selector item.
     *
     * @param name  The name of the policy to invoke.
     * @param param The parameter to pass to the name constructor.
     */
    public PolicyDirective(String name, String param) {
        this.name = name;
        this.param = param;
    }

    @Override
    public boolean matches(HopDirective dir) {
        return true;
    }

    /**
     * Returns the name of the policy that this item is to invoke.
     *
     * @return The name name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the parameter string for this policy directive.
     *
     * @return The parameter.
     */
    public String getParam() {
        return param;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PolicyDirective)) {
            return false;
        }
        PolicyDirective rhs = (PolicyDirective)obj;
        if (!name.equals(rhs.name)) {
            return false;
        }
        if (param == null && rhs.param != null) {
            return false;
        }
        if (param != null && !param.equals(rhs.param)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "[" + name + (param != null && param.length() > 0 ? ":" + param : "") + "]";
    }

    @Override
    public String toDebugString() {
        return "PolicyDirective(name = '" + name + "', param = '" + param + "')";
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (param != null ? param.hashCode() : 0);
        return result;
    }
}
