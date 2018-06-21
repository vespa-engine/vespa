// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.yahoo.collections.Pair;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.config.ConfigInstance;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.vespa.config.ConfigKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.container.di.componentgraph.core.Exceptions.cutStackTraceAtConstructor;
import static com.yahoo.container.di.componentgraph.core.Exceptions.removeStackTrace;
import static com.yahoo.container.di.componentgraph.core.Keys.createKey;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class ComponentNode extends Node {
    private static final Logger log = Logger.getLogger(ComponentNode.class.getName());

    private final Class<?> clazz;
    private final Annotation key;
    private Object[] arguments = null;
    private final String configId;

    private final Constructor<?> constructor;

    private Map<ConfigKey<ConfigInstance>, ConfigInstance> availableConfigs = null;


    public ComponentNode(ComponentId componentId,
                         String configId,
                         Class<?> clazz, Annotation XXX_key) // TODO expose key, not javaAnnotation
    {
        super(componentId);
        if (isAbstract(clazz)) {
            throw new IllegalArgumentException("Can't instantiate abstract class " + clazz.getName());
        }
        this.configId = configId;
        this.clazz = clazz;
        this.key = XXX_key;
        this.constructor = bestConstructor(clazz);
    }

    public ComponentNode(ComponentId componentId, String configId, Class<?> clazz) {
        this(componentId, configId, clazz, null);
    }

    public String configId() {
        return configId;
    }

    @Override
    public Key<?> instanceKey() {
        return createKey(clazz, key);
    }

    @Override
    public Class<?> instanceType() {
        return clazz;
    }

    @Override
    public List<Node> usedComponents() {
        if (arguments == null) {
            throw new IllegalStateException("Arguments must be set first.");
        }
        List<Node> ret = new ArrayList<>();
        for (Object arg : arguments) {
            if (arg instanceof Node) {
                ret.add((Node) arg);
            }
        }
        return ret;
    }

    private static List<Class<?>> allSuperClasses(Class<?> clazz) {
        List<Class<?>> ret = new ArrayList<>();
        while (clazz != null) {
            ret.add(clazz);
            clazz = clazz.getSuperclass();
        }
        return ret;
    }

    @Override
    public Class<?> componentType() {
        if (Provider.class.isAssignableFrom(clazz)) {
            //TODO: Test what happens if you ask for something that isn't a class, e.g. a parameterized type.

            List<Type> allGenericInterfaces = allSuperClasses(clazz).stream().flatMap(c -> Arrays.stream(c.getGenericInterfaces())).collect(Collectors.toList());
            for (Type t : allGenericInterfaces) {
                if (t instanceof ParameterizedType && ((ParameterizedType) t).getRawType().equals(Provider.class)) {
                    Type[] typeArgs = ((ParameterizedType) t).getActualTypeArguments();
                    if (typeArgs != null && typeArgs.length > 0) {
                        return (Class<?>) typeArgs[0];
                    }
                }
            }
            throw new IllegalStateException("Component type cannot be resolved");
        } else {
            return clazz;
        }
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    @Override
    protected Object newInstance() {
        if (arguments == null) {
            throw new IllegalStateException("graph.complete must be called before retrieving instances.");
        }

        List<Object> actualArguments = new ArrayList<>();
        for (Object ob : arguments) {
            if (ob instanceof Node) {
                actualArguments.add(((Node) ob).newOrCachedInstance());
            } else if (ob instanceof ConfigKey) {
                actualArguments.add(availableConfigs.get(ob));
            } else {
                actualArguments.add(ob);
            }
        }

        Object instance;
        try {
            instance = constructor.newInstance(actualArguments.toArray());
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            StackTraceElement dependencyInjectorMarker = new StackTraceElement("============= Dependency Injection =============", "newInstance", null, -1);

            throw removeStackTrace(new ComponentConstructorException("Error constructing " + idAndType(), cutStackTraceAtConstructor(e.getCause(), dependencyInjectorMarker)));
        }

        return initId(instance);
    }

    private Object initId(Object component) {
        if (component instanceof AbstractComponent) {
            AbstractComponent abstractComponent = (AbstractComponent) component;
            if (abstractComponent.hasInitializedId() && !abstractComponent.getId().equals(componentId())) {
                throw new IllegalStateException(
                        "Component with id '" + componentId() + "' is trying to set its component id explicitly: '" + abstractComponent.getId() + "'. " +
                                "This is not allowed, so please remove any call to super() in your component's constructor.");
            }
            abstractComponent.initId(componentId());
        }
        return component;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(arguments);
        result = prime * result + ((availableConfigs == null) ? 0 : availableConfigs.hashCode());
        result = prime * result + ((configId == null) ? 0 : configId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ComponentNode) {
            ComponentNode that = (ComponentNode) other;
            return super.equals(that) && equalEdges(Arrays.asList(this.arguments), Arrays.asList(that.arguments)) && this.usedConfigs().equals(that.usedConfigs());
        } else {
            return false;
        }
    }

    private List<ConfigInstance> usedConfigs() {
        if (availableConfigs == null) {
            throw new IllegalStateException("setAvailableConfigs must be called!");
        }
        List<ConfigInstance> ret = new ArrayList<>();
        for (Object arg : arguments) {
            if (arg instanceof ConfigKey) {
                ret.add(availableConfigs.get(arg));
            }
        }
        return ret;
    }

    protected List<Pair<Type, List<Annotation>>> getAnnotatedConstructorParams() {
        Type[] types = constructor.getGenericParameterTypes();
        Annotation[][] annotations = constructor.getParameterAnnotations();

        List<Pair<Type, List<Annotation>>> ret = new ArrayList<>();

        for (int i = 0; i < types.length; i++) {
            ret.add(new Pair<>(types[i], Arrays.asList(annotations[i])));
        }
        return ret;
    }

    public void setAvailableConfigs(Map<ConfigKey<ConfigInstance>, ConfigInstance> configs) {
        if (arguments == null) {
            throw new IllegalStateException("graph.complete must be called before graph.setAvailableConfigs.");
        }
        this.availableConfigs = configs;
    }

    @Override
    public Set<ConfigKey<ConfigInstance>> configKeys() {
        return configParameterClasses().stream().map(par -> new ConfigKey<>(par, configId)).collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private List<Class<ConfigInstance>> configParameterClasses() {
        List<Class<ConfigInstance>> ret = new ArrayList<>();
        for (Type type : constructor.getGenericParameterTypes()) {
            if (type instanceof Class && ConfigInstance.class.isAssignableFrom((Class<?>) type)) {
                ret.add((Class<ConfigInstance>) type);
            }
        }
        return ret;
    }

    @Override
    public String label() {
        LinkedList<String> configNames = configKeys().stream().map(k -> k.getName() + ".def").collect(Collectors.toCollection(LinkedList::new));

        configNames.addFirst(instanceType().getSimpleName());
        configNames.addFirst(Node.packageName(instanceType()));

        return "{" + String.join("|", configNames) + "}";
    }

    private static Constructor<?> bestConstructor(Class<?> clazz) {
        Constructor<?>[] publicConstructors = clazz.getConstructors();

        Constructor<?> annotated = null;
        for (Constructor<?> ctor : publicConstructors) {
            Annotation annotation = ctor.getAnnotation(Inject.class);
            if (annotation != null) {
                if (annotated == null) {
                    annotated = ctor;
                } else {
                    throw componentConstructorException("Multiple constructor annotated with @Inject in class " + clazz.getName());
                }
            }
        }
        if (annotated != null) {
            return annotated;
        }

        if (publicConstructors.length == 0) {
            throw componentConstructorException("No public constructors in class " + clazz.getName());
        } else if (publicConstructors.length == 1) {
            return publicConstructors[0];
        } else {
            log.warning(String.format("Multiple public constructors found in class %s, there should only be one. "
                    + "If more than one public constructor is needed, the primary one must be annotated with @Inject.", clazz.getName()));
            List<Pair<Constructor<?>, Integer>> withParameterCount = new ArrayList<>();
            for (Constructor<?> ctor : publicConstructors) {
                long count = Arrays.stream(ctor.getParameterTypes()).filter(ConfigInstance.class::isAssignableFrom).count();
                withParameterCount.add(new Pair<>(ctor, (int) count));
            }
            withParameterCount.sort(Comparator.comparingInt(Pair::getSecond));
            return withParameterCount.get(withParameterCount.size() - 1).getFirst();
        }
    }

    private static ComponentConstructorException componentConstructorException(String message) {
        return removeStackTrace(new ComponentConstructorException(message));
    }

    public static class ComponentConstructorException extends RuntimeException {
        ComponentConstructorException(String message) {
            super(message);
        }

        ComponentConstructorException(String message, Throwable cause) {
            super(message, cause);
        }
    }


    private static boolean isAbstract(Class<?> clazz) {
        return Modifier.isAbstract(clazz.getModifiers());
    }
}