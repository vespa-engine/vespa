// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.yahoo.config.codegen.CNode;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.codegen.LeafCNode;

/**
 * Represents an instance of an application config with a specific configId.
 * <p>
 * An instance of this class contains all values (represented by Nodes) for the config object as it
 * is the superclass of the generated config class used by the client.
 */
public abstract class ConfigInstance extends InnerNode {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ConfigInstance.class.getName());

    public interface Builder extends ConfigBuilder {

        /**
         * Dispatches a getConfig() call if this instance's producer is of the right type
         *
         * @param producer a config producer
         * @return true if this instance's producer was the correct type, and hence a getConfig call was dispatched
         */
        boolean dispatchGetConfig(Producer producer);

        String getDefName();
        String getDefNamespace();
        String getDefMd5();

        /** Returns true if this instance should be applied on restart, false if it should be applied immediately */
        default boolean getApplyOnRestart() { return false; }

        default void setApplyOnRestart(boolean applyOnRestart) { throw new java.lang.UnsupportedOperationException(); }

        default ConfigInstance buildInstance(InnerCNode targetDef) {
            try {
                if (targetDef != null) applyDef(this, targetDef);
                Class<? extends ConfigInstance> clazz = getConfigClass(getClass());
                return clazz.getConstructor(getClass()).newInstance(this);
            } catch (Exception e) {
                throw new ConfigurationRuntimeException(e);
            }
        }

        /**
         * If some fields on the builder are null now, set them from the def. Do recursively.
         * <p>
         * If the targetDef has some schema incompatibilities, they are not handled here
         * (except logging in some cases), but in ConfigInstance.serialize().
         *
         * @param  builder a {@link com.yahoo.config.ConfigBuilder}
         * @param  targetDef a config definition
         * @throws Exception if applying values form config definitions fails
         */
        static void applyDef(ConfigBuilder builder, InnerCNode targetDef) throws Exception {
            for (Map.Entry<String, CNode> e: targetDef.children().entrySet()) {
                CNode node = e.getValue();
                if (node instanceof LeafCNode) {
                    setLeafValueIfUnset(targetDef, builder, (LeafCNode)node);
                } else if (node instanceof InnerCNode) {
                    // Is there a private field on the builder that matches this inner node in the def?
                    if (hasField(builder.getClass(), node.getName())) {
                        Field innerField = builder.getClass().getDeclaredField(node.getName());
                        innerField.setAccessible(true);
                        Object innerFieldVal = innerField.get(builder);
                        if (innerFieldVal instanceof List) {
                            // inner array? Check that list elems are ConfigBuilder
                            List<?> innerList = (List<?>) innerFieldVal;
                            for (Object b : innerList) {
                                if (b instanceof ConfigBuilder) {
                                    applyDef((ConfigBuilder) b, (InnerCNode) node);
                                }
                            }
                        } else if (innerFieldVal instanceof ConfigBuilder) {
                            // Struct perhaps
                            applyDef((ConfigBuilder) innerFieldVal, (InnerCNode) node);
                        } else {
                            // Likely a config value mismatch. That is handled in ConfigInstance.serialize() (error message, omit from response.)
                        }
                    }
                }
            }
        }

        private static boolean hasField(Class<?> aClass, String name) {
            for (Field field : aClass.getDeclaredFields()) {
                if (name.equals(field.getName())) {
                    return true;
                }
            }
            return false;
        }

        private static void setLeafValueIfUnset(InnerCNode targetDef, Object builder, LeafCNode node) throws Exception {
            if (hasField(builder.getClass(), node.getName())) {
                Field field = builder.getClass().getDeclaredField(node.getName());
                field.setAccessible(true);
                Object val = field.get(builder);
                if (val==null) {
                    // Not set on builder, if the leaf node has a default value, try the private setter that takes String
                    try {
                        if (node.getDefaultValue()!=null) {
                            Method setter = builder.getClass().getDeclaredMethod(node.getName(), String.class);
                            setter.setAccessible(true);
                            setter.invoke(builder, node.getDefaultValue().getValue());
                        }
                    } catch (Exception e) {
                        log.log(Level.SEVERE,
                                "For config '" + targetDef.getFullName() + "': " +
                                "Unable to apply the default value for field '" + node.getName() +
                                "' to config Builder (where it wasn't set)",
                                e);
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static Class<? extends ConfigInstance> getConfigClass(Class<? extends ConfigInstance.Builder> builderClass) {
            Class<?> configClass = builderClass.getEnclosingClass();
            if (configClass == null || ! ConfigInstance.class.isAssignableFrom(configClass)) {
                throw new ConfigurationRuntimeException("Builder class " + builderClass + " has enclosing class " + configClass + ", which is not a ConfigInstance");
            }
            return (Class<? extends ConfigInstance>) configClass;
        }

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
