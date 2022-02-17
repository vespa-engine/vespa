// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Properties;

import java.util.Map;

/**
 * A properties implementation which translates the incoming name to its standard name
 * if it is a registered alias.
 * <p>
 * Aliases are case insensitive. One standard name may have multiple aliases.
 * <p>
 * This is multithread safe or not depending on the status of the passed map of aliases.
 * Cloning will not deep copy the set of aliases.
 *
 * @author bratseth
 */
public class PropertyAliases extends Properties {

    /** A map from aliases to standard names */
    private final Map<String, CompoundName> aliases;

    /**
     * Creates an instance with a set of aliases. The given aliases will be used directly by this class.
     * To make this class immutable and thread safe, relinquish ownership of the parameter map.
     */
    public PropertyAliases(Map<String, CompoundName> aliases) {
        this.aliases = aliases;
    }

    /**
     * Returns the standard name for an alias, or the given name if it is not a registered alias
     *
     * @param nameOrAlias the name to check if is an alias
     * @return the real name if an alias or the input name itself
     */
    protected CompoundName unalias(CompoundName nameOrAlias) {
        if (aliases.isEmpty()) return nameOrAlias;
        CompoundName properName = aliases.get(nameOrAlias.getLowerCasedName());
        return (properName != null) ? properName : nameOrAlias;
    }

    @Override
    public Map<String, Object> listProperties(CompoundName property,
                                              Map<String,String> context,
                                              com.yahoo.processing.request.Properties substitution) {
        return super.listProperties(unalias(property), context, substitution);
    }

    @Override
    public Object get(CompoundName name, Map<String,String> context,
                                com.yahoo.processing.request.Properties substitution) {
        return super.get(unalias(name),context,substitution);
    }

    @Override
    public void set(CompoundName name, Object value, Map<String,String> context) {
        super.set(unalias(name), value, context);
    }

}
