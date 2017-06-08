// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.*;
import com.yahoo.config.codegen.CNode;
import com.yahoo.config.codegen.ConfigGenerator;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.codegen.LeafCNode;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * This class is capable of resolving config from a config model for a given request. It will handle
 * incompatibilities of the def version in the request and the version of the config classes the model
 * is using.
 * </p>
 * <p>
 * This class is agnostic of transport protocol and server implementation.
 * </p>
 * <p>
 * Thread safe.
 * </p>
 *
 * @author vegardh
 * @since 5.1.5
 */
// TODO  This functionality should be on VespaModel itself, but we don't have a way right now to apply a config override to a ConfigInstance.Builder
class InstanceResolver {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(InstanceResolver.class.getName());

    /**
     * Resolves this config key into a correctly typed ConfigInstance using the given config builder.
     * FIXME: Make private once config overrides are deprecated.?
     *
     * @param key   a ConfigKey
     * @param builder a ConfigBuilder to create the instance from.
     * @param targetDef the def to use
     * @return the config instance or null of no producer for this found in model
     */
    @SuppressWarnings("unchecked")
    static ConfigInstance resolveToInstance(ConfigKey<?> key, ConfigBuilder builder, InnerCNode targetDef) {
        try {
            if (targetDef != null) applyDef(builder, targetDef);
            Class<?> clazz = builder.getClass().getEnclosingClass();
            if (!(ConfigInstance.class.isAssignableFrom(clazz))) {
                throw new ConfigurationRuntimeException("Cannot produce config for the name '" + key.getName() + ", as "
                                                                + clazz.getName() + " is not a ConfigInstance.");
            }
            Class<? extends ConfigInstance> configClass = (Class<? extends ConfigInstance>)clazz;
            Constructor<? extends ConfigInstance> constructor = configClass.getDeclaredConstructor(builder.getClass(), boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(builder, /*throwIfUninitialized*/ false);
        } catch (Exception e) {
            throw new ConfigurationRuntimeException(e);
        }
    }

    /**
     * Resolves this config key into a correctly typed ConfigBuilder using the given config model.
     * FIXME: Make private once config overrides are deprecated.?
     *
     * @return the config builder or null if no producer for this found in model
     */
    static ConfigBuilder resolveToBuilder(ConfigKey<?> key, VespaModel model, ConfigDefinition targetDef) {
        if (model == null) return null;
        ConfigDefinitionKey defKey = new ConfigDefinitionKey(key);
        ConfigInstance.Builder builder = model.createBuilder(defKey, targetDef);
        model.getConfig(builder, key.getConfigId());
        return builder;
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
                    log.severe("For config '"+targetDef.getFullName()+"': Unable to apply the default value for field '"+node.getName()+
                            "' to config Builder (where it wasn't set): "+
                            Exceptions.toMessageString(e));
                }
            }
        }
    }

    static String packageName(ConfigDefinitionKey cKey) {
        String prefix = "com.yahoo.";
        return prefix + (cKey.getNamespace().isEmpty() ? CNode.DEFAULT_NAMESPACE : cKey.getNamespace());
    }

}
