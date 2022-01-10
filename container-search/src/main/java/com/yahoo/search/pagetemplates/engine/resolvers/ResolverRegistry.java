// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.resolvers;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.pagetemplates.engine.Resolver;

import java.util.List;
import java.util.logging.Logger;

/**
 * A registry of available resolver components
 *
 * @author bratseth
 */
public class ResolverRegistry extends ComponentRegistry<Resolver> {

    private final Resolver defaultResolver;

    public ResolverRegistry(List<Resolver> resolvers) {
        addBuiltInResolvers();
        for (Resolver component : resolvers)
            registerResolver(component);
        defaultResolver = decideDefaultResolver();
        freeze();
    }

    private void addBuiltInResolvers() {
        registerResolver(createNativeDeterministicResolver());
        registerResolver(createNativeRandomResolver());
    }

    private Resolver decideDefaultResolver() {
        Resolver defaultResolver = getComponent("default");
        if (defaultResolver != null) return defaultResolver;
        return getComponent("native.random");
    }

    private Resolver createNativeRandomResolver() {
        RandomResolver resolver = new RandomResolver();
        resolver.initId(ComponentId.fromString(RandomResolver.nativeId));
        return resolver;
    }

    private DeterministicResolver createNativeDeterministicResolver() {
        DeterministicResolver resolver = new DeterministicResolver();
        resolver.initId(ComponentId.fromString(DeterministicResolver.nativeId));
        return resolver;
    }

    private void registerResolver(Resolver resolver) {
        super.register(resolver.getId(), resolver);
    }

    public Resolver defaultResolver() { return defaultResolver; }

}
