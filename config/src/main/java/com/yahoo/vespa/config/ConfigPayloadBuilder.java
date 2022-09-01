// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper class for building Slime config payloads, while supporting referring to payloads with their indices. The
 * builder does not care about config field types. This is resolved by the actual config type consumer created
 * from the Slime tree.
 *
 * @author Ulf Lilleengen
 */
public class ConfigPayloadBuilder {

    private String value;
    private final Map<String, ConfigPayloadBuilder> objectMap;
    private final Map<String, Array> arrayMap;
    private final Map<String, MapBuilder> mapBuilderMap;
    private final ConfigDefinition configDefinition;

    /**
     * Construct a payload builder that is not a leaf.
     */
    public ConfigPayloadBuilder() {
        this(null, null);
    }

    public ConfigPayloadBuilder(ConfigDefinition configDefinition) {
        this(configDefinition, null);
    }

    /**
     * Construct a payload builder with a leaf value.
     *
     * @param value the value of this leaf
     */
    private ConfigPayloadBuilder(String value) {
        this(null, value);
    }

    private ConfigPayloadBuilder(ConfigDefinition configDefinition, String value) {
        this.objectMap = new LinkedHashMap<>();
        this.arrayMap = new LinkedHashMap<>();
        this.mapBuilderMap = new LinkedHashMap<>();
        this.value = value;
        this.configDefinition = configDefinition;
    }

    /**
     * Set the value of a config field.
     *
     * @param name  Name of the config field.
     * @param value Value of the config field.
     */
    public void setField(String name, String value) {
        validateField(name, value);
        objectMap.put(name, new ConfigPayloadBuilder(value));
    }

    private void validateField(String name, String value) {
        if (configDefinition != null) {
            configDefinition.verify(name, value);
        }
    }

    /**
     * Get a new payload builder for a config struct, which can be used to add inner values to that struct.
     *
     * @param name name of the struct to create
     * @return a payload builder corresponding to the name
     */
    public ConfigPayloadBuilder getObject(String name) {
        ConfigPayloadBuilder p = objectMap.get(name);
        if (p == null) {
            validateObject(name);
            p = new ConfigPayloadBuilder(getStructDef(name));
            objectMap.put(name, p);
        }
        return p;
    }

    private ConfigDefinition getStructDef(String name) {
        return (configDefinition == null ? null : configDefinition.getStructDefs().get(name));
    }

    private void validateObject(String name) {
        if (configDefinition != null) {
            configDefinition.verify(name);
        }
    }

    /**
     * Create a new array where new values may be added.
     *
     * @param name Name of array.
     * @return Array object supporting adding elements to it.
     */
    public Array getArray(String name) {
        Array a = arrayMap.get(name);
        if (a == null) {
            validateArray(name);
            a = new Array(configDefinition, name);
            arrayMap.put(name, a);
        }
        return a;
    }

    private void validateArray(String name) {
        if (configDefinition != null) {
            configDefinition.verify(name);
        }
    }

    /**
     * Create slime tree from this builder.
     *
     * @param parent the parent Cursor for this builder
     */
    public void resolve(Cursor parent) {
        // TODO: Fix so that names do not clash
        for (Map.Entry<String, ConfigPayloadBuilder> entry : objectMap.entrySet()) {
            String name = entry.getKey();
            ConfigPayloadBuilder value = entry.getValue();
            if (value.getValue() == null) {
                Cursor childCursor = parent.setObject(name);
                value.resolve(childCursor);
            } else {
                // TODO: Support giving correct type
                parent.setString(name, value.getValue());
            }
        }
        for (Map.Entry<String, ConfigPayloadBuilder.Array> entry : arrayMap.entrySet()) {
            Cursor array = parent.setArray(entry.getKey());
            entry.getValue().resolve(array);
        }
        for (Map.Entry<String, MapBuilder> entry : mapBuilderMap.entrySet()) {
            String name = entry.getKey();
            MapBuilder map = entry.getValue();
            Cursor cursormap = parent.setObject(name);
            map.resolve(cursormap);
        }
    }

    public ConfigPayloadBuilder override(ConfigPayloadBuilder other) {
        value = other.value;
        for (Map.Entry<String, ConfigPayloadBuilder> entry : other.objectMap.entrySet()) {
            String key = entry.getKey();
            ConfigPayloadBuilder value = entry.getValue();
            if (objectMap.containsKey(key)) {
                objectMap.put(key, objectMap.get(key).override(value));
            } else {
                objectMap.put(key, new ConfigPayloadBuilder(value));
            }
        }
        for (Map.Entry<String, Array> entry : other.arrayMap.entrySet()) {
            String key = entry.getKey();
            Array value = entry.getValue();
            if (arrayMap.containsKey(key)) {
                arrayMap.put(key, arrayMap.get(key).override(value));
            } else {
                arrayMap.put(key, new Array(value));
            }
        }
        mapBuilderMap.putAll(other.mapBuilderMap);
        return this;
    }


    /**
     * Get the value of this field, if any.
     *
     * @return value of field, null if this is not a leaf
     */
    public String getValue() { return value; }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Create a new map where new values may be added.
     *
     * @param name Name of map.
     * @return Map builder supporting adding elements to it.
     */
    public MapBuilder getMap(String name) {
        MapBuilder a = mapBuilderMap.get(name);
        if (a == null) {
            validateMap(name);
            a = new MapBuilder(configDefinition, name);
            mapBuilderMap.put(name, a);
        }
        return a;
    }

    private void validateMap(String name) {
        if (configDefinition != null) {
            configDefinition.verify(name);
        }
    }

    public ConfigDefinition getConfigDefinition() {
        return configDefinition;
    }

    public class MapBuilder {

        private final Map<String, ConfigPayloadBuilder> elements = new LinkedHashMap<>();
        private final ConfigDefinition configDefinition;
        private final String name;
        public MapBuilder(ConfigDefinition configDefinition, String name) {
            this.configDefinition = configDefinition;
            this.name = name;
        }

        public void put(String key, String value) {
            elements.put(key, new ConfigPayloadBuilder(getLeafMapDef(name), value));
        }

        public ConfigPayloadBuilder put(String key) {
            ConfigPayloadBuilder p = new ConfigPayloadBuilder(getStructMapDef(name));
            elements.put(key, p);
            return p;
        }

        public ConfigPayloadBuilder get(String key) {
            ConfigPayloadBuilder builder = elements.get(key);
            if (builder == null) {
                builder = put(key);
            }
            return builder;
        }

        public void resolve(Cursor parent) {
            for (Map.Entry<String, ConfigPayloadBuilder> entry : elements.entrySet()) {
                ConfigPayloadBuilder child = entry.getValue();
                String childVal = child.getValue();
                if (childVal != null) {
                    parent.setString(entry.getKey(), childVal);
                } else {
                    Cursor childCursor = parent.setObject(entry.getKey());
                    child.resolve(childCursor);
                }
            }
        }

        private ConfigDefinition.LeafMapDef getLeafMapDef(String name) {
            return (configDefinition == null ? null : configDefinition.getLeafMapDefs().get(name));
        }

        private ConfigDefinition getStructMapDef(String name) {
            return (configDefinition == null ? null : configDefinition.getStructMapDefs().get(name));
        }

        public Collection<ConfigPayloadBuilder> getElements() {
            return elements.values();
        }
    }

    /**
     * Array modes.
     */
    private enum ArrayMode {
        INDEX, APPEND
    }

    @Override
    public String toString() {
        return "config builder of " + getConfigDefinition();
    }

    /**
     * Representation of a config array, which supports both INDEX and APPEND modes.
     */
    public class Array {
        private final Map<Integer, ConfigPayloadBuilder> elements = new LinkedHashMap<>();
        private ArrayMode mode = ArrayMode.INDEX;
        private final String name;
        private final ConfigDefinition configDefinition;

        public Array(ConfigDefinition configDefinition, String name) {
            this.configDefinition = configDefinition;
            this.name = name;
        }

        public Array(Array other) {
            this.elements.putAll(other.elements);
            this.mode = other.mode;
            this.name = other.name;
            this.configDefinition = other.configDefinition;
        }

        /**
         * Append a value to this array.
         *
         * @param value Value to append.
         */
        public void append(String value) {
            setAppend();
            validateArrayElement(getArrayDef(name), value, elements.size());
            ConfigPayloadBuilder p = new ConfigPayloadBuilder(getArrayDef(name), value);
            elements.put(elements.size(), p);
        }

        private void validateArrayElement(ConfigDefinition.ArrayDef arrayDef, String value, int index) {
            if (arrayDef != null) {
                arrayDef.verify(value, index);
            }
        }

        private ConfigDefinition.ArrayDef getArrayDef(String name) {
            return (configDefinition == null ? null : configDefinition.getArrayDefs().get(name));
        }

        private ConfigDefinition getInnerArrayDef(String name) {
            return (configDefinition == null ? null : configDefinition.getInnerArrayDefs().get(name));
        }

        public Collection<ConfigPayloadBuilder> getElements() {
            return elements.values();
        }

        /**
         * Create a new slime object and returns its payload builder. Append the element after all other elements
         * in the array.
         *
         * @return a payload builder for the new slime object.
         */
        public ConfigPayloadBuilder append() {
            setAppend();
            ConfigPayloadBuilder p = new ConfigPayloadBuilder(getInnerArrayDef(name));
            elements.put(elements.size(), p);
            return p;
        }

        /**
         * Set the value of array element index to value
         *
         * @param index Index of array element to set.
         * @param value Value that the element should point to.
         */
        public void set(int index, String value) {
            verifyIndex();
            ConfigPayloadBuilder p = new ConfigPayloadBuilder(value);
            elements.put(index, p);
        }

        /**
         * Set Create a payload object for the given index and return it. Any previously stored version will be
         * overwritten.
         *
         * @param index Index of new element.
         * @return The payload builder for the newly created slime object.
         */
        public ConfigPayloadBuilder set(int index) {
            verifyIndex();
            ConfigPayloadBuilder p = new ConfigPayloadBuilder(getInnerArrayDef(name));
            elements.put(index, p);
            return p;
        }

        /**
         * Get payload builder in this array corresponding to index. If it does not exist, create a new one.
         *
         * @param index of element to get.
         * @return The corresponding ConfigPayloadBuilder.
         */
        public ConfigPayloadBuilder get(int index) {
            ConfigPayloadBuilder builder = elements.get(index);
            if (builder == null) {
                if (mode == ArrayMode.APPEND)
                    builder = append();
                else
                    builder = set(index);
            }
            return builder;
        }

        /**
         * Try to set append mode, but do some checking if indexed mode has been used first.
         */
        private void setAppend() {
            if (mode == ArrayMode.INDEX && elements.size() > 0) {
                throw new IllegalStateException("Cannot append elements to an array in index mode with more than one element");
            }
            mode = ArrayMode.APPEND;
        }

        /**
         * Try and verify that index mode is possible.
         */
        private void verifyIndex() {
            if (mode == ArrayMode.APPEND)
                throw new IllegalStateException("Cannot reference array elements with index once append is done");
        }

        public void resolve(Cursor parent) {
            for (Map.Entry<Integer, ConfigPayloadBuilder> entry : elements.entrySet()) {
                ConfigPayloadBuilder child = entry.getValue();
                String childVal = child.getValue();
                if (childVal != null) {
                    parent.addString(childVal);
                } else {
                    Cursor childCursor = parent.addObject();
                    child.resolve(childCursor);
                }
            }
        }

        public Array override(Array superior) {
            if (mode == ArrayMode.INDEX && superior.mode == ArrayMode.INDEX) {
                elements.putAll(superior.elements);
            } else {
                for (ConfigPayloadBuilder builder : superior.elements.values()) {
                    append().override(builder);
                }
            }
            return this;
        }
    }

    private ConfigPayloadBuilder(ConfigPayloadBuilder other) {
        this.arrayMap = other.arrayMap;
        this.mapBuilderMap = other.mapBuilderMap;
        this.value = other.value;
        this.objectMap = other.objectMap;
        this.configDefinition = other.configDefinition;
    }

    public ConfigPayloadBuilder(ConfigPayload payload) {
        this(new BuilderDecoder(payload.getSlime()).decode(payload.getSlime().get()));
    }

    private static class BuilderDecoder {

        private final Slime slime;
        public BuilderDecoder(Slime slime) {
            this.slime = slime;
        }

        ConfigPayloadBuilder decode(Inspector element) {
            ConfigPayloadBuilder root = new ConfigPayloadBuilder();
            decodeObject(slime, root, element);
            return root;
        }

        private static void decodeObject(Slime slime, ConfigPayloadBuilder builder, Inspector element) {
            BuilderObjectTraverser traverser = new BuilderObjectTraverser(slime, builder);
            element.traverse(traverser);
        }

        private static void decode(Slime slime, String name, Inspector inspector, ConfigPayloadBuilder builder) {
            switch (inspector.type()) {
                case STRING -> builder.setField(name, inspector.asString());
                case LONG -> builder.setField(name, String.valueOf(inspector.asLong()));
                case DOUBLE -> builder.setField(name, String.valueOf(inspector.asDouble()));
                case BOOL -> builder.setField(name, String.valueOf(inspector.asBool()));
                case OBJECT -> decodeObject(slime, builder.getObject(name), inspector);
                case ARRAY -> decodeArray(slime, builder.getArray(name), inspector);
            }
        }

        private static void decodeArray(Slime slime, Array array, Inspector inspector) {
            BuilderArrayTraverser traverser = new BuilderArrayTraverser(slime, array);
            inspector.traverse(traverser);
        }

        private static class BuilderObjectTraverser implements ObjectTraverser {

            private final ConfigPayloadBuilder builder;
            private final Slime slime;

            public BuilderObjectTraverser(Slime slime, ConfigPayloadBuilder builder) {
                this.slime = slime;
                this.builder = builder;
            }

            @Override
            public void field(String name, Inspector inspector) {
                decode(slime, name, inspector, builder);
            }

        }

        private static class BuilderArrayTraverser implements ArrayTraverser {

            private final Array array;
            private final Slime slime;

            public BuilderArrayTraverser(Slime slime, Array array) {
                this.array = array;
                this.slime = slime;
            }

            @Override
            public void entry(int idx, Inspector inspector) {
                switch (inspector.type()) {
                    case STRING -> array.append(inspector.asString());
                    case OBJECT -> decodeObject(slime, array.append(), inspector);
                }
            }

        }

    }

}
