package com.yahoo.component.chain.model;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.dependencies.Provides;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Components which can be chained together, and where dependency information is provided through annotations.
 *
 * @author jonmv
 */
public interface Chainable {

    default Dependencies getAnnotatedDependencies() {
        Set<String> provides = new LinkedHashSet<>();
        Set<String> before = new LinkedHashSet<>();
        Set<String> after = new LinkedHashSet<>();

        for (Annotation annotation : getClass().getAnnotations()) {
            if (annotation instanceof Provides p) provides.addAll(List.of(p.value()));
            if (annotation instanceof com.yahoo.yolean.chain.Provides p) provides.addAll(List.of(p.value()));

            if (annotation instanceof Before b) before.addAll(List.of(b.value()));
            if (annotation instanceof com.yahoo.yolean.chain.Before b) before.addAll(List.of(b.value()));

            if (annotation instanceof After a) after.addAll(List.of(a.value()));
            if (annotation instanceof com.yahoo.yolean.chain.After a) after.addAll(List.of(a.value()));
        }

        provides.add(getClass().getSimpleName());
        provides.add(getClass().getName());

        return new Dependencies(provides, before, after);
    }

}