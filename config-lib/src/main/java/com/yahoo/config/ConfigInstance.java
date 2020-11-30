// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents an instance of an application config with a specific configId.
 * <p>
 * An instance of this class contains all values (represented by Nodes) for the config object as it
 * is the superclass of the generated config class used by the client.
 */
public abstract class ConfigInstance extends InnerNode {

    public interface Builder extends ConfigBuilder {

        /**
         * Dispatches a getConfig() call if this instance's producer is of the right type
         * @param producer a config producer
         * @return true if this instance's producer was the correct type, and hence a getConfig call was dispatched
         */
        boolean dispatchGetConfig(Producer producer);

        String getDefName();
        String getDefNamespace();
        String getDefMd5();

    }

    public interface Producer {}

    private String configMd5 = "";

    @SuppressWarnings("unused") // Used by reflection from ConfigInstanceUtil
    String configId;

    /**
     * Gets the name of the given config instance
     */
    public static String getDefName(Class<?> type) {
        return getStaticStringField(type, "CONFIG_DEF_NAME");
    }

    /**
     * Gets the namespace of the given config instance
     */
    public static String getDefNamespace(Class<?> type) {
        return getStaticStringField(type, "CONFIG_DEF_NAMESPACE");
    }

    /**
     * Returns the serialized representation of the given node.
     * <p>
     * Declared static, instead of InnerNode member, to avoid a public 0-arg method with a commonly used name.
     *
     * @param node The inner node
     * @return a list of strings, containing the serialized representation of this config
     */
    public static List<String> serialize(InnerNode node) {
        List<String> ret = new ArrayList<>();
        for (Map.Entry<String, LeafNode<?>> entry : getAllDescendantLeafNodes(node).entrySet()) {
            ret.add(entry.getKey() + " " + entry.getValue().toString());
        }
        return ret;
    }

    public static void serialize(InnerNode node, Serializer serializer) {
        serializeMap(node.getChildren(), serializer);
    }

    @SuppressWarnings("unchecked")
    private static void serializeObject(String name, Object child, Serializer serializer) {
        if (child instanceof InnerNode) {
            Serializer childSerializer = serializer.createInner(name);
            serialize((InnerNode) child, childSerializer);
        } else if (child instanceof Map) {
            Serializer mapSerializer = serializer.createMap(name);
            serializeMap((Map<String, Object>)child, mapSerializer);
        } else if (child instanceof NodeVector) {
            Serializer arraySerializer = serializer.createArray(name);
            serializeArray((NodeVector) child, arraySerializer);
        } else if (child instanceof LeafNode) {
            ((LeafNode) child).serialize(name, serializer);
        }
    }

    private static void serializeMap(Map<String, Object> childMap, Serializer serializer) {
        for (Map.Entry<String, Object> entry : childMap.entrySet()) {
            String name = entry.getKey();
            Object child = entry.getValue();
            serializeObject(name, child, serializer);
        }
    }

    private static void serializeArray(NodeVector<?> nodeVector, Serializer arraySerializer) {
        for (Object child : nodeVector.vector) {
            if (child instanceof InnerNode) {
                Serializer childSerializer = arraySerializer.createInner();
                serialize((InnerNode) child, childSerializer);
            } else if (child instanceof LeafNode) {
                ((LeafNode) child).serialize(arraySerializer);
            }
        }
    }


    public String getConfigMd5() {
        return configMd5;
    }

    public void setConfigMd5(String configMd5) {
        this.configMd5 = configMd5;
    }

    private static String getStaticStringField(Class<?> type, String fieldName) {
        try {
            return (String) type.getField(fieldName).get(null);
        } catch (Exception e) {
            throw new RuntimeException
                    (e.getMessage() + ": Static field " + fieldName + " not " + "accessible in " + type.getName());
        }
    }

}
