// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.provider;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

/**
 * Encapsulates the class of a component to be created, along with the constructor that will be used.
 *
 * @author gjoranv
 * @author bratseth
 */
public class ComponentClass<T extends AbstractComponent> {
    private static Logger log = Logger.getLogger(ComponentClass.class.getName());

    private final Class<T> clazz;
    private final ComponentConstructor<T> constructor;

    public ComponentClass(Class<T> clazz) {
        this.clazz = clazz;
        constructor = findPreferredConstructor();
        if (! constructor.isLegal) {
            throw new IllegalArgumentException("Class '" + clazz.getName() + "' must have at least one public " +
                    "constructor with an optional component ID followed by an optional FileAcquirer and " +
                    "zero or more config arguments: " +
                    clazz.getSimpleName() + "([ComponentId] [ConfigInstance ...])");
        }
    }

    /**
     * Create an instance of this ComponentClass with the given configId. The configs needed by the component
     * must exist in the provided set of {@link com.yahoo.config.ConfigInstance}s.
     *
     * @param id                  The id of the component to create, never null.
     * @param availableConfigs    The set of available config instances.
     * @param configId            The config ID of the component, nullable.
     * @return A new instance of the class represented by this ComponentClass.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public T createComponent(ComponentId id, Map<ConfigKey, ConfigInstance> availableConfigs, String configId) {
        if (configId == null) {
            configId = System.getProperty("config.id");
        }

        boolean hasId = false;
        List<Object> params = new LinkedList<>();
        for (Class cc : constructor.parameters) {
            if (cc.equals(ComponentId.class)) {
                params.add(id);
                hasId = true;
           } else if (cc.getSuperclass().equals(ConfigInstance.class)) {
                ConfigKey key = new ConfigKey(cc, configId);
                if ((availableConfigs == null) || ! availableConfigs.containsKey(key)) {
                    throw new IllegalStateException
                            ("Could not resolve config instance '" + key + "' required to instantiate " + clazz);
                }
                params.add(availableConfigs.get(key));
            }
        }
        T component = construct(params.toArray());

        if (hasId && component.hasInitializedId() && !id.equals(component.getId())) {
            log.warning("Component with id '" + id + "' tried to set illegal component id: '" + component.getId() +
                    "', or the component takes ComponentId as a constructor arg without calling super(id).");
        }
        // Enforce correct id - see bug #4036397
        component.initId(id);

        return component;
    }

    public ComponentConstructor<T> getPreferredConstructor() {
        return constructor;
    }

    /**
     * Creates an instance of this class. Due to the error-prone Object varargs, this method must be used with
     * caution, and never from outside this class.
     *
     * @param arguments the arguments to the constructor
     * @return The new instance.
     * @throws RuntimeException if construction fails for some reason
     */
    private T construct(Object... arguments) {
        String args = Arrays.toString(arguments);
        try {
            return constructor.getConstructor().newInstance(arguments);
        } catch (InstantiationException e) {
            throw new RuntimeException("Exception while instantiating " + clazz + " from " + args,e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access " + constructor + " of " + clazz);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Exception while executing constructor of " + clazz + " with " + args,e);
        } catch (IllegalArgumentException e) {
            log.warning(clazz.getName() + " expected ctor arguments:");
            for (@SuppressWarnings("rawtypes") Class expectedArg : constructor.getConstructor().getParameterTypes())
                log.warning("   " + expectedArg + " - " + System.identityHashCode(expectedArg));

            log.warning(clazz.getName() + " actual ctor arguments: ");
            for (Object actualArg : arguments)
                log.warning("   " + actualArg.getClass() + " - " + System.identityHashCode(actualArg.getClass()));
            throw new RuntimeException("Exception while executing constructor of " + clazz + " with " + args,e);
        }

    }

    /**
     * Returns the preferred constructor of the given class, or null if no satisfactory constructor is present.
     * The preferred constructor is always the one with the most arguments of type T extends ConfigInstance.
     *
     * @return The preferred constructor.
     */
    @SuppressWarnings("unchecked")
    private ComponentConstructor<T> findPreferredConstructor() {
        @SuppressWarnings("rawtypes")
        Constructor[] constructors = clazz.getConstructors();
        if (constructors.length < 1) {
            throw new RuntimeException("Class has no public constructors: " + clazz.getName());
        }
        ComponentConstructor<T> best = new ComponentConstructor<T>(constructors[0]);
        for (int i = 1; i < constructors.length; i++) {
            Constructor<T> c = constructors[i];
            ComponentConstructor<T> cc = new ComponentConstructor<>(c);
            if (cc.preferredTo(best)) {
                best = cc;
            }
        }
        return best;
    }

    /**
     * Encapsulates a constructor for a ComponentClass. Immutable.
     */
    public static class ComponentConstructor<T> {

        // The legal argument classes (except '? extends ConfigInstance' of course)
        @SuppressWarnings("rawtypes")
        private static final Set<Class> legalArgs = Collections.singleton((Class) ComponentId.class);

        private final Constructor<T> constructor;

        @SuppressWarnings("rawtypes")
        private final Class[] parameters;
        private final List<Class<? extends ConfigInstance>> configArgs;

        public final boolean isLegal;
        public final boolean hasComponentId;

        public ComponentConstructor(Constructor<T> c) {
            constructor = c;
            parameters = c.getParameterTypes();

            isLegal = isLegal(parameters);
            hasComponentId = hasComponentId(parameters);
            configArgs = findConfigArgs(parameters);
        }

        public Constructor<T> getConstructor() {
            return constructor;
        }

        /**
         * Returns true if this constructor is preferred to the other, or if they are equivalent.
         * False otherwise.
         * @param other  The other constructor.
         * @return  true if this constructor is preferred to the other, false otherwise.
         */
        public boolean preferredTo(ComponentConstructor<T> other) {
            if (this.isLegal && ! other.isLegal)
                return true;
            else if (! this.isLegal && other.isLegal)
                return false;

            // Both are legal
            if (this.parameters.length > other.parameters.length)
                return true;
            else if (this.parameters.length < other.parameters.length)
                return false;

            // Equal number of args
            if (this.configArgs.size() > other.configArgs.size())
                return true;
            else if (this.configArgs.size() < other.configArgs.size())
                return false;

            // Equal number of args and config args, prefer ComponentId
            if (this.hasComponentId  && ! other.hasComponentId)
                return true;
            else if (! this.hasComponentId && other.hasComponentId)
                return false;

            // Equivalent
            return true;
        }

        @SuppressWarnings("rawtypes")
        private static boolean isLegal(Class[] args) {
            Set<Class> used = new HashSet<>();
            for (Class cl : args) {
                if (legalArgs.contains(cl)) {
                    if (used.contains(cl)) {
                        return false;
                    }
                    if (cl.equals(String.class) || cl.equals(ComponentId.class)) {
                        // Only one of these are allowed, so mark both as used.
                        used.add(String.class);
                        used.add(ComponentId.class);
                    } else {
                        used.add(cl);
                    }
                } else {
                    // Must be a config arg
                    Class superclass = cl.getSuperclass();
                    if ((superclass == null) || !superclass.equals(com.yahoo.config.ConfigInstance.class))
                        return false;
                }
            }
            return true;
        }

        @SuppressWarnings("rawtypes")
        private static boolean hasComponentId(Class[] args) {
            for (Class cl : args) {
                if (cl.equals(ComponentId.class))
                    return true;
            }
            return false;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static List<Class<? extends ConfigInstance>> findConfigArgs(Class[] args) {
            List<Class<? extends ConfigInstance>> configs = new ArrayList<>();
            for (Class cl : args) {
                Class superclass = cl.getSuperclass();
                if ((superclass != null) && superclass.equals(ConfigInstance.class)) {
                    configs.add(cl);
                }
            }
            return configs;
        }

    } // class ComponentConstructor

}
