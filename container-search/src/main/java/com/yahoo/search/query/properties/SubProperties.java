// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.Properties;

import java.util.Map;

/**
 * A wrapper around a chain of property objects that prefixes all gets/sets with a given path
 *
 * @author Arne Bergene Fossaa
 * @deprecated Unused and will go away on vespa 9
 */
@Deprecated (forRemoval = true)
public class SubProperties extends com.yahoo.search.query.Properties {

    final private CompoundName pathPrefix;
    final private Properties parent;

    public SubProperties(String pathPrefix, Properties properties) {
        this(CompoundName.from(pathPrefix), properties);
    }

    public SubProperties(CompoundName pathPrefix, Properties properties) {
        this.pathPrefix = pathPrefix;
        this.parent = properties;
    }

    @Override
    public Object get(CompoundName key, Map<String,String> context,
                      com.yahoo.processing.request.Properties substitution) {
        if(key == null) return null;
        Object result = parent.get(getPathPrefix() + "." + key,context,substitution);
        if(result == null) {
            return super.get(key,context,substitution);
        } else {
            return result;
        }
    }

    @Override
    public void set(CompoundName key, Object obj, Map<String,String> context) {
        if(key == null) return;
        parent.set(getPathPrefix() + "." + key, obj, context);
    }

    @Override
    public Map<String, Object> listProperties(CompoundName path,Map<String,String> context,
                                              com.yahoo.processing.request.Properties substitution) {
        Map<String, Object> map = super.listProperties(path,context,substitution);
        if(path.isEmpty()) {
            map.putAll(parent.listProperties(getPathPrefix(),context,substitution));
        } else {
            map.putAll(parent.listProperties(getPathPrefix() + "." + path,context,substitution));
        }
        return map;
    }

    public CompoundName getPathPrefixCompound() {
        return pathPrefix;
    }

    /** Returns getPatchPrefixCompound.toString() */
    public String getPathPrefix() {
        return getPathPrefixCompound().toString();
    }

}
