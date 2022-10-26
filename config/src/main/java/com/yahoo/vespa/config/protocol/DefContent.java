// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.slime.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
* @author Ulf Lilleengen
*/
public class DefContent {
    private final List<String> data;

    private DefContent(List<String> data) {
        this.data = data;
    }

    public String[] asStringArray() {
        return data.toArray(new String[data.size()]);
    }

    public List<String> asList() {
        return data;
    }

    public String asString() {
        return com.yahoo.text.StringUtilities.implode(asStringArray(), "\n");
    }

    static DefContent fromSlime(Inspector data) {
        final List<String> lst = new ArrayList<>();
        data.traverse((ArrayTraverser) (idx, inspector) -> lst.add(inspector.asString()));
        return new DefContent(lst);
    }

    public static DefContent fromClass(Class<? extends ConfigInstance> clazz) {
        return fromArray(defSchema(clazz));
    }

    public static DefContent fromList(List<String> def) {
        return new DefContent(def);
    }

    public static DefContent fromArray(String[] schema) {
        return fromList(Arrays.asList(schema));
    }

    /**
     * The def file payload of the actual class of the given config.
     *
     * @param configClass the class of a generated config instance
     * @return a String array with the config definition (one line per element)
     */
    private static String[] defSchema(Class<? extends ConfigInstance> configClass) {
        if (configClass==null) return new String[0];
        try {
            Field f = configClass.getField("CONFIG_DEF_SCHEMA");
            return (String[]) f.get(configClass);
        } catch (NoSuchFieldException e) {
            return new String[0];
        } catch (Exception e) {
            throw new ConfigurationRuntimeException(e);
        }
    }

    public void serialize(final Cursor cursor) {
        for (String line : data) {
            cursor.addString(line);
        }
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }
}
