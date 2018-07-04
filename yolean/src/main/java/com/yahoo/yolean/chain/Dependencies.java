// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class Dependencies<T> {

    final Order<T> before;
    final Order<T> after;
    final List<String> provided;

    private Dependencies(Order<T> before, Order<T> after, String[] provided) {
        this.before = before;
        this.after = after;
        this.provided = copyList(provided);
    }

    @SafeVarargs
    public static <T> Dependencies<T> before(T... components) {
        return new Dependencies<>(new Order<>(components, null, null), Order.<T>emptyOrder(), null);
    }

    @SafeVarargs
    public static <T> Dependencies<T> before(Class<? extends T>... classes) {
        return new Dependencies<>(new Order<>(null, classes, null), Order.<T>emptyOrder(), null);
    }

    public static <T> Dependencies<T> before(String... providedNames) {
        return new Dependencies<>(new Order<T>(null, null, providedNames), Order.<T>emptyOrder(), null);
    }

    @SafeVarargs
    public static <T> Dependencies<T> after(T... components) {
        return new Dependencies<>(Order.<T>emptyOrder(), new Order<>(components, null, null), null);
    }

    @SafeVarargs
    public static <T> Dependencies<T> after(Class<? extends T>... classes) {
        return new Dependencies<>(Order.<T>emptyOrder(), new Order<>(null, classes, null), null);
    }

    public static <T> Dependencies<T> after(String... providedNames) {
        return new Dependencies<>(Order.<T>emptyOrder(), new Order<T>(null, null, providedNames), null);
    }

    public static <T> Dependencies<T> provides(String... names) {
        return new Dependencies<>(Order.<T>emptyOrder(), Order.<T>emptyOrder(), names);
    }

    public static <T> Dependencies<T> emptyDependencies() {
        return new Dependencies<>(Order.<T>emptyOrder(), Order.<T>emptyOrder(), null);
    }

    @SuppressWarnings("unchecked")
    static <T> Dependencies<T> union(List<Dependencies<? extends T>> dependenciesList) {
        if (dependenciesList.size() > 1) {
            Dependencies<T> result = emptyDependencies();
            for (Dependencies<? extends T> dependencies : dependenciesList) {
                result = result.union(dependencies);
            }
            return result;
        } else if (dependenciesList.size() == 0) {
            return emptyDependencies();
        } else {
            return (Dependencies<T>)dependenciesList.get(0); // Dependencies<T> is covariant for T, the cast is valid.
        }
    }

    private Dependencies<T> union(Dependencies<? extends T> other) {
        List<String> lst = listUnion(provided, other.provided);
        return new Dependencies<>(before.union(other.before),
                                  after.union(other.after),
                                  lst.toArray(new String[lst.size()]));
    }

    private static <T> List<T> listUnion(List<? extends T> list1, List<? extends T> list2) {
        List<T> union = new ArrayList<>(list1);
        union.removeAll(list2);
        union.addAll(list2);
        return union;
    }

    static <T> Dependencies<T> getAnnotatedDependencies(T component) {
        return new Dependencies<>(
                new Order<T>(null, null, getSymbols(component, Before.class)),
                new Order<T>(null, null, getSymbols(component, After.class)),
                getProvidedSymbols(component));
    }

    private static <T> String[] getProvidedSymbols(T component) {
        List<String> lst = allOf(getSymbols(component, Provides.class), component.getClass().getName());
        return lst.toArray(new String[lst.size()]);
    }

    @SafeVarargs
    static <T> List<T> allOf(List<T> elements, T... otherElements) {
        List<T> result = new ArrayList<>(elements);
        result.addAll(Arrays.asList(otherElements));
        return result;
    }

    @SafeVarargs
    static <T> List<T> allOf(T[] elements, T... otherElements) {
        return allOf(Arrays.asList(elements), otherElements);
    }

    private static <T> List<String> getSymbols(T component, Class<? extends Annotation> annotationClass) {
        List<String> result = new ArrayList<>();

        result.addAll(annotationSymbols(component, annotationClass));
        return result;
    }

    private static <T> Collection<String> annotationSymbols(T component, Class<? extends Annotation> annotationClass) {
        try {
            List<String> values = new ArrayList<>();

            Class<?> clazz = component.getClass();
            while (clazz != null) {
                Annotation annotation = clazz.getAnnotation(annotationClass);
                if (annotation != null) {
                    values.addAll(Arrays.asList((String[])annotationClass.getMethod("value").invoke(annotation)));
                }
                clazz = clazz.getSuperclass();
            }
            return values;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static <U> List<U> copyList(List<U> list) {
        return list == null ?
               Collections.<U>emptyList() :
               new ArrayList<>(list);
    }

    private static <U> List<U> copyList(U[] array) {
        return array == null ?
               Collections.<U>emptyList() :
               new ArrayList<>(Arrays.<U>asList(array));
    }

    static final class Order<T> {

        final List<T> instances;
        final List<Class<? extends T>> classes;
        final List<String> providedNames;

        private Order(T[] instances, Class<? extends T>[] classes, String[] providedNames) {
            this.instances = copyList(instances);
            this.classes = copyList(classes);
            this.providedNames = copyList(providedNames);
        }

        private Order(List<T> instances, List<Class<? extends T>> classes, List<String> providedNames) {
            this.instances = copyList(instances);
            this.classes = copyList(classes);
            this.providedNames = copyList(providedNames);
        }

        // TODO: unit test
        private Order<T> union(Order<? extends T> other) {
            return new Order<>(
                    listUnion(instances, other.instances),
                    listUnion(classes, other.classes),
                    listUnion(providedNames, other.providedNames));
        }

        // TODO: try to make it possible to use 'null' Order in Dependencies instead.
        private static <U> Order<U> emptyOrder() {
            return new Order<>((U[])null, null, null);
        }
    }

}
