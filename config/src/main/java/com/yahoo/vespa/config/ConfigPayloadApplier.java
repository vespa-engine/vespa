// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigBuilder;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.FileReference;
import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Type;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

/**
 * A utility class that can be used to apply a payload to a config builder.
 *
 * TODO: This can be refactored a lot, since many of the reflection methods are duplicated
 *
 * @author Ulf Lilleengen
 * @author hmusum
 * @author Tony Vaagenes
 */
public class ConfigPayloadApplier<T extends ConfigInstance.Builder> {

    private final static Logger log = Logger.getLogger(ConfigPayloadApplier.class.getPackage().getName());

    private final ConfigInstance.Builder rootBuilder;
    private final ConfigTransformer.PathAcquirer pathAcquirer;
    private final UrlDownloader urlDownloader;
    private final Deque<NamedBuilder> stack = new ArrayDeque<>();

    public ConfigPayloadApplier(T builder) {
        this(builder, new IdentityPathAcquirer(), null);
    }

    public ConfigPayloadApplier(T builder, ConfigTransformer.PathAcquirer pathAcquirer, UrlDownloader urlDownloader) {
        this.rootBuilder = builder;
        this.pathAcquirer = pathAcquirer;
        this.urlDownloader = urlDownloader;
    }

    public void applyPayload(ConfigPayload payload) {
        stack.push(new NamedBuilder(rootBuilder));
        try {
            handleValue(payload.getSlime().get());
        } catch (FileReferenceDoesNotExistException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Not able to create config builder for payload '" + payload.toString() + "'", e);
        }
    }

    private void handleValue(Inspector inspector) {
        switch (inspector.type()) {
            case NIX, BOOL, LONG, DOUBLE, STRING, DATA -> handleLeafValue(inspector);
            case ARRAY -> handleARRAY(inspector);
            case OBJECT -> handleOBJECT(inspector);
            default -> {
                assert false : "Should not be reached";
            }
        }
    }

    private void handleARRAY(Inspector inspector) {
        inspector.traverse((ArrayTraverser) this::handleArrayEntry);
    }

    private void handleArrayEntry(int idx, Inspector inspector) {
        try {
            String name = stack.peek().nameStack().peek();
            if (inspector.type() == Type.OBJECT) {
                NamedBuilder builder = createBuilder(stack.peek(), name);
                if (builder == null) return;  // Ignore non-existent struct array class
                stack.push(builder);
            }
            handleValue(inspector);
            if (inspector.type() == Type.OBJECT) {
                stack.peek().nameStack().pop();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleOBJECT(Inspector inspector) {
        inspector.traverse(this::handleObjectEntry);
        NamedBuilder builder = stack.pop();

        // Need to set e.g struct(Struct.Builder) here
        if ( ! stack.isEmpty()) {
            try {
                invokeSetter(stack.peek().builder, builder.peekName(), builder.builder);
            } catch (Exception e) {
                throw new RuntimeException("Could not set '" + builder.peekName() +
                                           "' for value '" + builder.builder() + "'", e);
            }
        }
    }

    private void handleObjectEntry(String name, Inspector inspector) {
        try {
            NamedBuilder parentBuilder = stack.peek();
            if (inspector.type() == Type.OBJECT) {
                if (isMapField(parentBuilder, name)) {
                    parentBuilder.nameStack().push(name);
                    handleMap(inspector);
                    parentBuilder.nameStack().pop();
                    return;
                } else {
                    NamedBuilder builder = createBuilder(parentBuilder, name);
                    if (builder == null) return;  // Ignore non-existent struct class
                    stack.push(builder);
                }
            } else if (inspector.type() == Type.ARRAY) {
                for (int i = 0; i < inspector.children(); i++) {
                    parentBuilder.nameStack().push(name);
                }
            } else {  // leaf
                parentBuilder.nameStack().push(name);
            }
            handleValue(inspector);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleMap(Inspector inspector) {
        inspector.traverse((String name, Inspector value) -> {
            switch (value.type()) {
                case OBJECT -> handleInnerMap(name, value);
                case ARRAY -> throw new IllegalArgumentException("Never heard of array inside maps before");
                default -> setMapLeafValue(name, value);
            }
        });
    }

    private void handleInnerMap(String name, Inspector inspector) {
        NamedBuilder builder = createBuilder(stack.peek(), stack.peek().peekName());
        if (builder == null)
            throw new RuntimeException("Missing map builder (this should never happen): " + stack.peek());
        setMapLeafValue(name, builder.builder());
        stack.push(builder);
        inspector.traverse(this::handleObjectEntry);
        stack.pop();
    }

    private void setMapLeafValue(String key, Object value) {
        NamedBuilder parent = stack.peek();
        ConfigBuilder builder = parent.builder();
        String methodName = parent.peekName();
        try {
            invokeSetter(builder, methodName, key, resolveValue(builder, methodName, value));
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Name: " + methodName + ", value '" + value + "'", e);
        } catch (NoSuchMethodException e) {
            log.log(INFO, "Skipping unknown field " + methodName + " in " + rootBuilder);
        }
    }

    private boolean isMapField(NamedBuilder parentBuilder, String name) {
        ConfigBuilder builder = parentBuilder.builder();
        try {
            Field f = builder.getClass().getField(name);
            return f.getType().getName().equals("java.util.Map");
        } catch (Exception e) {
            return false;
        }
    }

    NamedBuilder createBuilder(NamedBuilder parentBuilder, String name) {
        Object builder = parentBuilder.builder();
        Object newBuilder = getBuilderForStruct(name, builder.getClass().getDeclaringClass());
        if (newBuilder == null) return null;
        return new NamedBuilder((ConfigBuilder) newBuilder, name);
    }

    private void handleLeafValue(Inspector value) {
        NamedBuilder peek = stack.peek();
        String name = peek.nameStack().pop();
        ConfigBuilder builder = peek.builder();
        setValueForLeafNode(builder, name, value);
    }

    // Sets values for leaf nodes (uses private accessors that take string as argument)
    private void setValueForLeafNode(Object builder, String methodName, Inspector value) {
        try {
            invokeSetter(builder, methodName, resolveValue(builder, methodName, value));
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Name: " + methodName + ", value '" + value + "'", e);
        } catch (NoSuchMethodException e) {
            log.log(INFO, "Skipping unknown field " + methodName + " in " + builder.getClass());
        }
    }

    private Object resolveValue(Object builder, String methodName, Object rawValue) {
        if (rawValue instanceof ConfigBuilder) // Value in a map
            return rawValue;
        Inspector value = (Inspector)rawValue;
        if (isPathField(builder, methodName))
            return resolvePath(value.asString());
        else if (isUrlField(builder, methodName))
            return value.asString().isEmpty() ? "" : resolveUrl(value.asString());
        else if (isModelField(builder, methodName))
            return value.asString().isEmpty() ? "" : resolveModel(value.asString());
        else
            return getValueFromInspector(value);
    }

    /**
     * This may run on both the config server and subscribing nodes.
     * We only have a urlDownloader set up when client side.
     */
    private boolean isClientside() {
        return urlDownloader != null;
    }

    private FileReference resolvePath(String value) {
        Path path = pathAcquirer.getPath(new FileReference(value));
        return new FileReference(path.toString());
    }

    private UrlReference resolveUrl(String url) {
        if ( ! isClientside()) return new UrlReference(url);
        File file = urlDownloader.waitFor(new UrlReference(url), 60 * 60);
        return new UrlReference(file.getAbsolutePath());
    }

    private ModelReference resolveModel(String modelStringValue) {
        var model = ModelReference.valueOf(modelStringValue);
        if (model.isResolved())
            return model;
        if (isClientside() && model.url().isPresent()) // url has priority
            return ModelReference.resolved(Path.of(resolveUrl(model.url().get().value()).value()));
        if (isClientside() && model.path().isPresent())
            return ModelReference.resolved(Path.of(resolvePath(model.path().get().value()).value()));
        return model;
    }

    private final Map<String, Method> methodCache = new HashMap<>();
    private static String methodCacheKey(Object builder, String methodName, Object[] params) {
        StringBuilder sb = new StringBuilder();
        sb.append(builder.getClass().getName())
          .append(".")
          .append(methodName);
        for (Object param : params) {
            sb.append(".").append(param.getClass().getName());
        }
        return sb.toString();
    }

    private Method lookupSetter(Object builder, String methodName, Object ... params) throws NoSuchMethodException {
        Class<?>[] parameterTypes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            parameterTypes[i] = params[i].getClass();
        }
        Method method = builder.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private void invokeSetter(Object builder, String methodName, Object ... params) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // TODO: Does not work for native types.
        String key = methodCacheKey(builder, methodName, params);
        Method method = methodCache.get(key);
        if (method == null) {
            method = lookupSetter(builder, methodName, params);
            methodCache.put(key, method);
        }
        method.invoke(builder, params);
    }

    private Object getValueFromInspector(Inspector inspector) {
        switch (inspector.type()) {
            case STRING -> {
                return inspector.asString();
            }
            case LONG -> {
                return String.valueOf(inspector.asLong());
            }
            case DOUBLE -> {
                return String.valueOf(inspector.asDouble());
            }
            case NIX -> {
                return null;
            }
            case BOOL -> {
                return String.valueOf(inspector.asBool());
            }
            case DATA -> {
                return String.valueOf(inspector.asData());
            }
        }
        throw new IllegalArgumentException("Unhandled type " + inspector.type());
    }


    /**
     * Checks if this field is of type 'path', in which
     * case some special handling might be needed. Caches the result.
     */
    private final Set<String> pathFieldSet = new HashSet<>();
    private boolean isPathField(Object builder, String methodName) {
        // Paths are stored as FileReference in Builder.
        return isFieldType(pathFieldSet, builder, methodName, FileReference.class);
    }

    private final Set<String> urlFieldSet = new HashSet<>();
    private boolean isUrlField(Object builder, String methodName) {
        // Urls are stored as UrlReference in Builder.
        return isFieldType(urlFieldSet, builder, methodName, UrlReference.class);
    }

    private final Set<String> modelFieldSet = new HashSet<>();
    private boolean isModelField(Object builder, String methodName) {
        // Models are stored as ModelReference in Builder.
        return isFieldType(modelFieldSet, builder, methodName, ModelReference.class);
    }

    private boolean isFieldType(Set<String> fieldSet, Object builder, String methodName, java.lang.reflect.Type type) {
        String key = fieldKey(builder, methodName);
        if (fieldSet.contains(key)) {
            return true;
        }
        boolean isType = false;
        try {
            Field field = builder.getClass().getDeclaredField(methodName);
            java.lang.reflect.Type fieldType = field.getGenericType();
            if (fieldType instanceof Class<?> && fieldType == type) {
                isType = true;
            } else if (fieldType instanceof ParameterizedType) {
                isType = isParameterizedWith((ParameterizedType) fieldType, type);
            }
        } catch (NoSuchFieldException e) {
        }
        if (isType) {
            fieldSet.add(key);
        }
        return isType;
    }

    private static String fieldKey(Object builder, String methodName) {
        return builder.getClass().getName() + "." + methodName;
    }

    private boolean isParameterizedWith(ParameterizedType fieldType, java.lang.reflect.Type type) {
        int numTypeArgs = fieldType.getActualTypeArguments().length;
        if (numTypeArgs > 0)
             return fieldType.getActualTypeArguments()[numTypeArgs - 1] == type;
        return false;
    }

    private String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private Constructor<?> lookupBuilderForStruct(String structName, Class<?> currentClass) {
        String currentClassName = currentClass.getName();
        Class<?> structClass = getInnerClass(currentClass, currentClassName + "$" + structName);
        if (structClass == null) {
            log.info("Could not find nested class '" + currentClassName + "$" + structName +
                             "'. Ignoring it, assuming it's been added to a newer version of the config.");
            return null;
        }
        return getStructBuilderConstructor(structClass, currentClassName, structName);
    }

    private Constructor<?> getStructBuilderConstructor(Class<?> structClass, String currentClassName, String builderName) {
        String structBuilderName = currentClassName + "$" + builderName + "$Builder";
        Class<?> structBuilderClass = getInnerClass(structClass, structBuilderName);
        if (structBuilderClass == null)
            throw new RuntimeException("Could not find builder class " + structBuilderName);
        try {
            return structBuilderClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not create class '" + "'" + structBuilderClass.getName() + "'");
        }
    }

    /**
     * Finds a nested class with the given <code>name</code>name in <code>clazz</code>.
     *
     * @param clazz a Class
     * @param name a name
     * @return class found, or null if no class is found
     */
    private Class<?> getInnerClass(Class<?> clazz, String name) {
        for (Class<?> cls : clazz.getDeclaredClasses()) {
            if (cls.getName().equals(name))
                return cls;
        }
        return null;
    }

    private final Map<String, Constructor<?>> constructorCache = new HashMap<>();
    private static String constructorCacheKey(String builderName, String name, Class<?> currentClass) {
        return builderName + "." + name + "." + currentClass.getName();
    }

    private Object getBuilderForStruct(String name, Class<?> currentClass) {
        String structName = capitalize(name);
        String key = constructorCacheKey(structName, name, currentClass);
        Constructor<?> constructor = constructorCache.get(key);
        if (constructor == null) {
            constructor = lookupBuilderForStruct(structName, currentClass);
            if (constructor == null) return null;
            constructorCache.put(key, constructor);
        }
        try {
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not create class '" + "'" + constructor.getDeclaringClass().getName() + "'");
        }
    }

    /**
     * A class that holds a builder and a stack of names
     */
    private static class NamedBuilder {

        private final ConfigBuilder builder;
        private final Deque<String> names = new ArrayDeque<>(); // if empty, the builder is the root builder

        NamedBuilder(ConfigBuilder builder) {
            this.builder = builder;
        }

        NamedBuilder(ConfigBuilder builder, String name) {
            this(builder);
            names.push(name);
        }

        ConfigBuilder builder() {
            return builder;
        }

        String peekName() {
            return names.peek();
        }

        Deque<String> nameStack() {
            return names;
        }

        @Override
        public String toString() {
            return builder() == null ? "null" : builder.toString() + " names=" + names;
        }
    }

    static class IdentityPathAcquirer implements ConfigTransformer.PathAcquirer {
        @Override
        public Path getPath(FileReference fileReference) {
            return new File(fileReference.value()).toPath();
        }
    }

}
