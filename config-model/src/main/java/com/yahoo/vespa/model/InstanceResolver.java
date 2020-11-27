// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.ConfigBuilder;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.codegen.CNode;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.codegen.LeafCNode;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.yolean.Exceptions;

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
 * @author Vegard Havdal
 */
// TODO: Most of this has been copied to ConfigInstance.Builder.buildInstance() and can be removed from here
//       when Model.getConfig is removed
class InstanceResolver {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(InstanceResolver.class.getName());

    /**
     * Resolves this config key into a correctly typed ConfigInstance using the given config builder.
     * FIXME: Make private once config overrides are deprecated.?
     *
     * @param builder a ConfigBuilder to create the instance from.
     * @param targetDef the def to use
     * @return the config instance or null of no producer for this found in model
     */
    static ConfigInstance resolveToInstance(ConfigInstance.Builder builder, InnerCNode targetDef) {
        try {
            if (targetDef != null) applyDef(builder, targetDef);
            Class<? extends ConfigInstance> clazz = getConfigClass(builder.getClass());
            return clazz.getConstructor(builder.getClass()).newInstance(builder);
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
                    log.severe("For config '"+targetDef.getFullName()+"': Unable to apply the default value for field '"+node.getName()+
                            "' to config Builder (where it wasn't set): "+
                            Exceptions.toMessageString(e));
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

    static String packageName(ConfigDefinitionKey cKey, PackagePrefix packagePrefix) {
        return packagePrefix.value + cKey.getNamespace();
    }

    enum PackagePrefix {
        COM_YAHOO("com.yahoo."),
        NONE("");

        final String value;
        PackagePrefix (String value) {
            this.value = value;
        }
    }

}
