// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.dependencies.Provides;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Component with dependencies.
 *
 * @author Tony Vaagenes
 */
public abstract class ChainedComponent extends AbstractComponent {

    /** The immutable set of dependencies of this. NOTE: the default is only for unit testing. */
    private Dependencies dependencies = getDefaultAnnotatedDependencies();

    public ChainedComponent(ComponentId id) {
        super(id);
    }

    protected ChainedComponent() {}

    /**
     * Called by the container to assign the full set of dependencies to this class (configured and declared).
     * This is called once before this is started.
     * @param  dependencies The configured dependencies, that this method will merge with annotated dependencies.
     */
    public void initDependencies(Dependencies dependencies) {
        this.dependencies = dependencies.union(getDefaultAnnotatedDependencies());
    }

    /** Returns the configured and declared dependencies of this chainedcomponent */
    public Dependencies getDependencies() { return dependencies; }

    /** This method is here only for legacy reasons, do not override. */
    protected Dependencies getDefaultAnnotatedDependencies() {
        Dependencies dependencies = getAnnotatedDependencies(com.yahoo.yolean.chain.Provides.class, com.yahoo.yolean.chain.Before.class, com.yahoo.yolean.chain.After.class);
        Dependencies legacyDependencies = getAnnotatedDependencies(Provides.class, Before.class, After.class);

        return dependencies.union(legacyDependencies);
    }

    /**
     * @param providesClass  The annotation class representing 'provides'.
     * @param beforeClass    The annotation class representing 'before'.
     * @param afterClass     The annotation class representing 'after'.
     * @return a new {@link Dependencies} created from the annotations given in this component's class.
     */
    protected Dependencies getAnnotatedDependencies(Class<? extends Annotation> providesClass,
                                                    Class<? extends Annotation> beforeClass,
                                                    Class<? extends Annotation> afterClass) {
        return new Dependencies(
                allOf(getSymbols(this, providesClass), this.getClass().getSimpleName(), this.getClass().getName()),
                getSymbols(this, beforeClass),
                getSymbols(this, afterClass));
    }

    // TODO: move to vespajlib.
    private static List<String> allOf(List<String> symbols, String... otherSymbols) {
        List<String> result = new ArrayList<>(symbols);
        result.addAll(Arrays.asList(otherSymbols));
        return result;
    }


    private static List<String> getSymbols(ChainedComponent component, Class<? extends Annotation> annotationClass) {
        List<String> result = new ArrayList<>();

        result.addAll(annotationSymbols(component, annotationClass));
        return result;
    }

    private static Collection<String> annotationSymbols(ChainedComponent component, Class<? extends Annotation> annotationClass) {

        try {
            Annotation annotation = component.getClass().getAnnotation(annotationClass);
            if (annotation != null) {
                Object values = annotationClass.getMethod("value").invoke(annotation);
                return Arrays.asList((String[])values);
            }
            return Collections.emptyList();

        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
