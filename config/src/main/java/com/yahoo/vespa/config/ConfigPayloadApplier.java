// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigBuilder;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.FileReference;
import com.yahoo.config.UrlReference;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Type;
import com.yahoo.text.Utf8;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;

/**
 * A utility class that can be used to apply a payload to a config builder.
 *
 * TODO: This can be refactored a lot, since many of the reflection methods are duplicated
 *
 * @author Ulf Lilleengen, hmusum, Tony Vaagenes
 */
public class ConfigPayloadApplier<T extends ConfigInstance.Builder> {

    private final static Logger log = Logger.getLogger(ConfigPayloadApplier.class.getPackage().getName());

    private final ConfigInstance.Builder rootBuilder;
    private final ConfigTransformer.PathAcquirer pathAcquirer;
    private final UrlDownloader urlDownloader;
    private final Stack<NamedBuilder> stack = new Stack<>();

    public ConfigPayloadApplier(T builder) {
        this(builder, new IdentityPathAcquirer(), null);
    }

    public ConfigPayloadApplier(T builder, ConfigTransformer.PathAcquirer pathAcquirer, UrlDownloader urlDownloader) {
        this.rootBuilder = builder;
        this.pathAcquirer = pathAcquirer;
        this.urlDownloader = urlDownloader;
        debug("rootBuilder=" + rootBuilder);
    }

    public void applyPayload(ConfigPayload payload) {
        stack.push(new NamedBuilder(rootBuilder));
        try {
            handleValue(payload.getSlime().get());
        } catch (Exception e) {
            throw new RuntimeException("Not able to create config builder for payload:" + payload.toString() +
                    ", " + Exceptions.toMessageString(e), e);
        }
    }

    private void handleValue(Inspector inspector) {
        switch (inspector.type()) {
            case NIX:
            case BOOL:
            case LONG:
            case DOUBLE:
            case STRING:
            case DATA:
                handleLeafValue(inspector);
                break;
            case ARRAY:
                handleARRAY(inspector);
                break;
            case OBJECT:
                handleOBJECT(inspector);
                break;
            default:
                assert false : "Should not be reached";
        }
    }

    private void handleARRAY(Inspector inspector) {
        trace("Array");
        inspector.traverse(new ArrayTraverser() {
            @Override
            public void entry(int idx, Inspector inspector) {
                handleArrayEntry(idx, inspector);
            }
        });
    }

    private void handleArrayEntry(int idx, Inspector inspector) {
        try {
            trace("entry, idx=" + idx);
            trace("top of stack=" + stack.peek().toString());
            String name = stack.peek().nameStack().peek();
            if (inspector.type().equals(Type.OBJECT)) {
                NamedBuilder builder = createBuilder(stack.peek(), name);
                if (builder == null) return;  // Ignore non-existent struct array class
                stack.push(builder);
            }
            handleValue(inspector);
            if (inspector.type().equals(Type.OBJECT)) {
                stack.peek().nameStack().pop();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleOBJECT(Inspector inspector) {
        trace("Object");
        printStack();

        inspector.traverse(new ObjectTraverser() {
            @Override
            public void field(String name, Inspector inspector) {
                handleObjectEntry(name, inspector);
            }
        });

        trace("Should pop a builder from stack");
        NamedBuilder builder = stack.pop();
        printStack();

        // Need to set e.g struct(Struct.Builder) here
        if (!stack.empty()) {
            trace("builder= " + builder);
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
            trace("field, name=" + name);
            NamedBuilder parentBuilder = stack.peek();
            if (inspector.type().equals(Type.OBJECT)) {
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
            } else if (inspector.type().equals(Type.ARRAY)) {
                for (int i = 0; i < inspector.children(); i++) {
                    trace("Pushing " + name);
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
        inspector.traverse(new ObjectTraverser() {
            @Override
            public void field(String name, Inspector inspector) {
                switch (inspector.type()) {
                case OBJECT:
                    handleInnerMap(name, inspector);
                    break;
                case ARRAY:
                    throw new IllegalArgumentException("Never herd of array inside maps before");
                default:
                    setMapLeafValue(name, getValueFromInspector(inspector));
                    break;
                }
            }
        });
    }

    private void handleInnerMap(String name, Inspector inspector) {
        NamedBuilder builder = createBuilder(stack.peek(), stack.peek().peekName());
        if (builder == null)
            throw new RuntimeException("Missing map builder (this should never happen): " + stack.peek());
        setMapLeafValue(name, builder.builder());
        stack.push(builder);
        inspector.traverse(new ObjectTraverser() {
            @Override
            public void field(String name, Inspector inspector) {
                handleObjectEntry(name, inspector);
            }
        });
        stack.pop();
    }

    private void setMapLeafValue(String key, Object value) {
        NamedBuilder parent = stack.peek();
        ConfigBuilder builder = parent.builder();
        String methodName = parent.peekName();
        //trace("class to obtain method from: " + builder.getClass().getName());
        try {
            // Need to convert reference into actual path if 'path' type is used
            if (isPathField(builder, methodName)) {
                FileReference wrappedPath = resolvePath((String)value);
                invokeSetter(builder, methodName, key, wrappedPath);

            // Need to convert url into actual file if 'url' type is used
            } else if (isUrlField(builder, methodName)) {
                UrlReference url = resolveUrl((String)value);
                invokeSetter(builder, methodName, key, url);

            } else {
                invokeSetter(builder, methodName, key, value);
            }
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
        trace("New builder for " + name + "=" + newBuilder);
        trace("Pushing builder for " + name + "=" + newBuilder + " onto stack");
        return new NamedBuilder((ConfigBuilder) newBuilder, name);
    }

    private void handleLeafValue(Inspector value) {
        trace("String ");
        printStack();
        NamedBuilder peek = stack.peek();
        trace("popping name stack");
        String name = peek.nameStack().pop();
        printStack();
        ConfigBuilder builder = peek.builder();
        trace("name=" + name + ",builder=" + builder + ",value=" + value.toString());
        setValueForLeafNode(builder, name, value);
    }

    // Sets values for leaf nodes (uses private accessors that take string as argument)
    private void setValueForLeafNode(Object builder, String methodName, Inspector value) {
        try {
            // Need to convert reference into actual path if 'path' type is used
            if (isPathField(builder, methodName)) {
                FileReference wrappedPath = resolvePath(Utf8.toString(value.asUtf8()));
                invokeSetter(builder, methodName, wrappedPath);

            // Need to convert url into actual file if 'url' type is used
            } else if (isUrlField(builder, methodName)) {
                UrlReference url = resolveUrl(Utf8.toString(value.asUtf8()));
                invokeSetter(builder, methodName, url);

            } else {
                Object object = getValueFromInspector(value);
                invokeSetter(builder, methodName, object);
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Name: " + methodName + ", value '" + value + "'", e);
        } catch (NoSuchMethodException e) {
            log.log(INFO, "Skipping unknown field " + methodName + " in " + builder.getClass());
        }
    }

    private FileReference resolvePath(String value) {
        Path path = pathAcquirer.getPath(newFileReference(value));
        return newFileReference(path.toString());
    }

    private UrlReference resolveUrl(String url) {
        if (urlDownloader == null) {
            throw new RuntimeException("Resolving url field failed due to missing URL downloader.");
        }
        File file = urlDownloader.waitFor(new UrlReference(url), 60 * 60);
        return new UrlReference(file.getAbsolutePath());
    }

    private FileReference newFileReference(String fileReference) {
        try {
            Constructor<FileReference> constructor = FileReference.class.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(fileReference);
        } catch (Exception e) {
            throw new RuntimeException("Failed invoking FileReference constructor.", e);
        }
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
        trace("method=" + method + ",params=" + params);
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
            case STRING:
                return Utf8.toString(inspector.asUtf8());
            case LONG:
                return String.valueOf(inspector.asLong());
            case DOUBLE:
                return String.valueOf(inspector.asDouble());
            case NIX:
                return null;
            case BOOL:
                return String.valueOf(inspector.asBool());
            case DATA:
                return String.valueOf(inspector.asData());
        }
        throw new IllegalArgumentException("Unhandled type " + inspector.type());
    }


    /**
     * Checks whether or not this field is of type 'path', in which
     * case some special handling might be needed. Caches the result.
     */
    private Set<String> pathFieldSet = new HashSet<>();
    private boolean isPathField(Object builder, String methodName) {
        // Paths are stored as FileReference in Builder.
        return isFieldType(pathFieldSet, builder, methodName, FileReference.class);
    }

    private Set<String> urlFieldSet = new HashSet<>();
    private boolean isUrlField(Object builder, String methodName) {
        // Urls are stored as UrlReference in Builder.
        return isFieldType(urlFieldSet, builder, methodName, UrlReference.class);
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
        StringBuilder sb = new StringBuilder();
        sb.append(name.substring(0, 1).toUpperCase()).append(name.substring(1));
        return sb.toString();
    }

    private Constructor<?> lookupBuilderForStruct(String structName, String name, Class<?> currentClass) {
        final String currentClassName = currentClass.getName();
        trace("structName=" + structName + ", name=" + name + ",current class=" + currentClassName);
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
     * Finds a nested class with the given <code>name</code>name in <code>clazz</code>
     * @param clazz a Class
     * @param name a name
     * @return class found, or null if no class is found
     */
    private Class<?> getInnerClass(Class<?> clazz, String name) {
        for (Class<?> cls : clazz.getDeclaredClasses()) {
            if (cls.getName().equals(name)) {
                trace("Found class " + cls.getName());
                return cls;
            }
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
        Constructor<?> ctor = constructorCache.get(key);
        if (ctor == null) {
            ctor = lookupBuilderForStruct(structName, name, currentClass);
            if (ctor == null) return null;
            constructorCache.put(key, ctor);
        }
        Object builder;
        try {
            builder = ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not create class '" + "'" + ctor.getDeclaringClass().getName() + "'");
        }
        return builder;
    }

    private void debug(String message) {
        log.log(FINE, () -> message);
    }

    private void trace(String message) {
        log.log(FINEST, () -> message);
    }

    private void printStack() {
        trace("stack=" + stack.toString());
    }

    /**
     * A class that holds a builder and a stack of names
     */
    private static class NamedBuilder {
        private final ConfigBuilder builder;
        private final Stack<String> names = new Stack<>(); // if empty, the builder is the root builder

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

        Stack<String> nameStack() {
            return names;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(builder() == null ? "null" : builder.toString()).append(" names=").append(names);
            return sb.toString();
        }
    }

    static class IdentityPathAcquirer implements ConfigTransformer.PathAcquirer {
        @Override
        public Path getPath(FileReference fileReference) {
            return new File(fileReference.value()).toPath();
        }
    }

}
