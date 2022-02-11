// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils.internal;

import com.google.common.reflect.TypeToken;
import com.yahoo.config.ChangesRequiringRestart;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.model.ConfigProducer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class containing static methods for retrieving information about the config producer tree.
 *
 * @author Ulf Lilleengen
 * @author bjorncs
 * @author gjoranv
 */
public final class ReflectionUtil {

    private ReflectionUtil() {}

    public static Set<ConfigKey<?>> getAllConfigsProduced(Class<? extends ConfigProducer> producerClass, String configId) {
        // TypeToken is @Beta in guava, so consider implementing a simple recursive method instead.
        TypeToken<? extends ConfigProducer>.TypeSet interfaces = TypeToken.of(producerClass).getTypes().interfaces();
        return interfaces.rawTypes().stream()
                .filter(ReflectionUtil::isConcreteProducer)
                .map(i -> createConfigKeyFromInstance(i.getEnclosingClass(), configId))
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

    /**
     * Determines if the config class contains the methods required for detecting config value changes
     * between two config instances.
     */
    public static boolean hasRestartMethods(Class<? extends ConfigInstance> configClass) {
        try {
            configClass.getDeclaredMethod("containsFieldsFlaggedWithRestart");
            configClass.getDeclaredMethod("getChangesRequiringRestart", configClass);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Determines if the config definition for the given config class contains key-values flagged with restart.
     */
    public static boolean containsFieldsFlaggedWithRestart(Class<? extends ConfigInstance> configClass)  {
        try {
            Method m = configClass.getDeclaredMethod("containsFieldsFlaggedWithRestart");
            m.setAccessible(true);
            return (boolean) m.invoke(null);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compares the config instances and lists any differences that will require service restart.
     *
     * @param from The previous config.
     * @param to The new config.
     * @return An object describing the difference.
     */
    public static ChangesRequiringRestart getChangesRequiringRestart(ConfigInstance from, ConfigInstance to) {
        Class<?> clazz = from.getClass();
        if (!clazz.equals(to.getClass())) {
            throw new IllegalArgumentException(String.format("%s != %s", clazz, to.getClass()));
        }
        try {
            Method m = clazz.getDeclaredMethod("getChangesRequiringRestart", clazz);
            m.setAccessible(true);
            return (ChangesRequiringRestart) m.invoke(from, to);
        }  catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Class<?>> getAllSuperclasses(Class<?> cls) {
        var result = new ArrayList<Class<?>>();
        for(Class<?> superClass = cls.getSuperclass(); superClass != null; superClass = superClass.getSuperclass()) {
            result.add(superClass);
        }
        return result;
    }

    private static ConfigKey<?> createConfigKeyFromInstance(Class<?> configInstClass, String configId) {
        String defName = ConfigInstance.getDefName(configInstClass);
        String defNamespace = ConfigInstance.getDefNamespace(configInstClass);
        return new ConfigKey<>(defName, configId, defNamespace);
    }

    private static boolean isConcreteProducer(Class<?> producerInterface) {
        if (isRootConfigProducerInterface(producerInterface))  return false;

        boolean parentIsConfigInstance = false;
        for (Class<?> ifaceParent : producerInterface.getInterfaces()) {
            if (isConfigInstanceProducer(ifaceParent)) {
                parentIsConfigInstance = true;
            }
        }
        return (ConfigInstance.Producer.class.isAssignableFrom(producerInterface)
                && parentIsConfigInstance
                && !isConfigInstanceProducer(producerInterface));
    }

    private static boolean isConfigInstanceProducer(Class<?> clazz) {
        return clazz.getName().equals(ConfigInstance.Producer.class.getName());
    }

    private static boolean isRootConfigProducerInterface(Class<?> clazz) {
        return clazz.getCanonicalName().equals(ConfigProducer.class.getCanonicalName());
    }

}
