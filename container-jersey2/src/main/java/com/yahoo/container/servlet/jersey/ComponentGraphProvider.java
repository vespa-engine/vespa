// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey;

import com.yahoo.container.di.config.ResolveDependencyException;
import com.yahoo.container.di.config.RestApiContext;
import com.yahoo.container.jaxrs.annotation.Component;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.api.ServiceHandle;

import javax.inject.Singleton;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves jdisc container components for jersey 2 components.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
@Singleton // jersey2 requirement: InjectionResolvers must be in the Singleton scope
public class ComponentGraphProvider implements InjectionResolver<Component> {
    private Collection<RestApiContext.Injectable> injectables;

    public ComponentGraphProvider(Collection<RestApiContext.Injectable> injectables) {
        this.injectables = injectables;
    }

    @Override
    public Object resolve(Injectee injectee, ServiceHandle<?> root) {
        Class<?> wantedClass;
        Type type = injectee.getRequiredType();
        if (type instanceof Class) {
            wantedClass = (Class<?>) type;
        } else {
            throw new UnsupportedOperationException("Only classes are supported, got " + type);
        }

        List<RestApiContext.Injectable> componentsWithMatchingType = new ArrayList<>();
        for (RestApiContext.Injectable injectable : injectables) {
            if (wantedClass.isInstance(injectable.instance)) {
                componentsWithMatchingType.add(injectable);
            }
        }

        if (componentsWithMatchingType.size() == 1) {
            return componentsWithMatchingType.get(0).instance;
        } else {
            String injectionDescription = "class '" + wantedClass + "' to inject into Jersey resource/provider '"
                    + injectee.getInjecteeClass() + "')";
            if (componentsWithMatchingType.size() > 1) {
                String ids = componentsWithMatchingType.stream().map(c -> c.id.toString()).collect(Collectors.joining(","));
                throw new ResolveDependencyException("Multiple components found of " + injectionDescription + ": " + ids);
            } else {
                throw new ResolveDependencyException("Could not find a component of " + injectionDescription + ".");
            }
        }
    }

    @Override
    public boolean isMethodParameterIndicator() {
        return true;
    }

    @Override
    public boolean isConstructorParameterIndicator() {
        return true;
    }
}
