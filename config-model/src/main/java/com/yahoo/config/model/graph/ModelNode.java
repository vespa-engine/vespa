// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.graph;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelInstanceFactory;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a node in the dependency graph, and contains information about a builders dependencies.
 * Constructor signatures of model classes must have ConfigModelContext as the first argument and
 * ConfigModel subclasses or Collection of ConfigModels as subsequent arguments.
 * Only Collection, not Collection subtypes can be used.
 *
 * @author Ulf Lilleengen
 */
public class ModelNode<MODEL extends ConfigModel> implements ConfigModelInstanceFactory<MODEL> {

    final ComponentId id;
    public final ConfigModelBuilder<MODEL> builder;
    final Class<MODEL> clazz;
    final Constructor<MODEL> constructor;
    final List<MODEL> instances = new ArrayList<>();
    private final Map<ComponentId, ModelNode> dependencies = new HashMap<>();

    public ModelNode(ConfigModelBuilder<MODEL> builder) {
        this.id = builder.getId();
        this.builder = builder;
        this.clazz = builder.getModelClass();
        this.constructor = findConstructor(clazz);
    }

    private Constructor<MODEL> findConstructor(Class<MODEL> clazz) {
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            if (ctor.getAnnotation(com.google.inject.Inject.class) != null
                    || ctor.getAnnotation(com.yahoo.component.annotation.Inject.class) != null) {
                return (Constructor<MODEL>) ctor;
            }
        }
        return (Constructor<MODEL>) clazz.getDeclaredConstructors()[0];
    }

    boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    boolean dependsOn(ModelNode node) {
        return dependencies.containsKey(node.id);
    }

    /**
     * This adds dependencies base on constructor arguments in the model classes themselves.
     * These then have to be created by this mini-di framework and then handed to the builders
     * that will fill them.
     *
     * TODO: This should be changed to model dependencies between model builders instead, such
     * that they can create their model objects, and eventually make them immutable.
     */
    int addDependenciesFrom(List<ModelNode> modelNodes) {
        int numDependencies = 0;
        for (Type param : constructor.getGenericParameterTypes()) {
            for (ModelNode node : modelNodes) {
                if (param.equals(node.clazz) || isCollectionOf(param, node.clazz)) {
                    addDependency(node);
                    numDependencies++;
                }
            }
        }
        return numDependencies;
    }

    private boolean isCollectionOf(Type type, Class<?> nodeClazz) {
        if (type instanceof ParameterizedType) {
            ParameterizedType t = (ParameterizedType) type;
            // Note: IntelliJ says the following cannot be equal but that is wrong
            return (t.getRawType().equals(java.util.Collection.class) && t.getActualTypeArguments().length == 1 && t.getActualTypeArguments()[0].equals(nodeClazz));
        }
        return false;
    }

    private boolean isCollection(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType t = (ParameterizedType) type;
            // Note: IntelliJ says the following cannot be equal but that is wrong
            return (t.getRawType().equals(java.util.Collection.class) && t.getActualTypeArguments().length == 1);
        }
        return false;
    }

    private void addDependency(ModelNode node) {
        dependencies.put(node.id, node);
    }

    Collection<ComponentId> listDependencyIds() {
        return dependencies.keySet();
    }

    @Override
    public MODEL createModel(ConfigModelContext context) {
        try {
            Type [] params = constructor.getGenericParameterTypes();
            if (params.length < 1 || ! params[0].equals(ConfigModelContext.class)) {
                throw new IllegalArgumentException("Constructor for " + clazz.getName() + " must have as its first argument a " + ConfigModelContext.class.getName());
            }
            Object arguments[] = new Object[params.length];
            arguments[0] = context;
            for (int i = 1; i < params.length; i++)
                arguments[i] = findArgument(params[i]);
            MODEL instance = constructor.newInstance(arguments);
            instances.add(instance);
            return instance;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Error constructing model '" + clazz.getName() + "'", e);
        }
    }

    private Object findArgument(Type param) {
        for (ModelNode dependency : dependencies.values()) {
            if (param.equals(dependency.clazz))
                return dependency.instances.get(0);
            if (isCollectionOf(param, dependency.clazz))
                return Collections.unmodifiableCollection(dependency.instances);
        }
        // For collections, we don't require that dependency has been added, we just give an empty collection
        if (isCollection(param))
            return Collections.emptyList();
        throw new IllegalArgumentException("Unable to find constructor argument " + param + " for " + clazz.getName());
    }

}
