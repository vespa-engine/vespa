// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.yahoo.config.ConfigBuilder;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.*;

/**
 * @author gjoranv
 */
public class ConfigInstanceUtil {

    /**
     * Copies all values that have been explicitly set on the source to the destination.
     * Values that have not been explicitly set in the source builder, will be left unchanged
     * in the destination.
     *
     * @param destination The builder to copy values into.
     * @param source The builder to copy values from. Unset values are not copied.
     * @param <BUILDER> The builder class.
     */
    public static<BUILDER extends ConfigBuilder> void setValues(BUILDER destination, BUILDER source) {
        try {
            Method setter = destination.getClass().getDeclaredMethod("override", destination.getClass());
            setter.setAccessible(true);
            setter.invoke(destination, source);
            setter.setAccessible(false);
        } catch (Exception e) {
            throw new ConfigurationRuntimeException("Could not set values on config builder." +
                                                    destination.getClass().getName(), e);
        }
    }

    public static <T extends ConfigInstance> T getNewInstance(Class<T> type,
                                                              String configId,
                                                              ConfigPayload payload) {
        T instance;
        try {
            ConfigTransformer<?> transformer = new ConfigTransformer<T>(type);
            ConfigBuilder instanceBuilder = transformer.toConfigBuilder(payload);
            Constructor<T> constructor = type.getConstructor(instanceBuilder.getClass());
            instance = constructor.newInstance((ConfigInstance.Builder) instanceBuilder);

            // Workaround for JDK7, where compilation fails due to fields being
            // private and not accessible from T. Reference it as a
            // ConfigInstance to work around it. See
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7022052 for
            // more information.
            ConfigInstance i = instance;
            i.postInitialize(configId);
            setConfigId(i, configId);

        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException |
                NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException("Failed creating new instance of '" + type.getCanonicalName() +
                    "' for config id '" + configId + "': " + Exceptions.toMessageString(e), e);
        }
        return instance;
    }

    private static void setConfigId(ConfigInstance instance, String configId)
            throws NoSuchFieldException, IllegalAccessException {
        Field configIdField = ConfigInstance.class.getDeclaredField("configId");
        configIdField.setAccessible(true);
        configIdField.set(instance, configId);
        configIdField.setAccessible(false);
    }

    /**
     * Gets the value of a private field on a Builder.
     * @param builder a {@link com.yahoo.config.ConfigBuilder}
     * @param fieldName a config field name
     * @return the value of the private field
     */
    public static Object getField(ConfigBuilder builder, String fieldName) {
        try {
            Field f = builder.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(builder);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
