// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.di.config.RestApiContext;
import com.yahoo.container.di.config.RestApiContext.BundleInfo;
import com.yahoo.container.jaxrs.annotation.Component;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.yahoo.container.servlet.jersey.util.ResourceConfigUtil.registerComponent;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class JerseyServletProvider implements Provider<ServletHolder> {
    private final ServletHolder jerseyServletHolder;

    public JerseyServletProvider(RestApiContext restApiContext) {
        this.jerseyServletHolder = new ServletHolder(new ServletContainer(resourceConfig(restApiContext)));
    }

    private ResourceConfig resourceConfig(RestApiContext restApiContext) {
        final ResourceConfig resourceConfig = ResourceConfig
                .forApplication(new JerseyApplication(resourcesAndProviders(restApiContext.getBundles())));

        registerComponent(resourceConfig, componentInjectorBinder(restApiContext));
        registerComponent(resourceConfig, jacksonDatatypeJdk8Provider());
        resourceConfig.register(MultiPartFeature.class);

        return resourceConfig;
    }

    private static Collection<Class<?>> resourcesAndProviders(Collection<BundleInfo> bundles) {
        final List<Class<?>> ret = new ArrayList<>();

        for (BundleInfo bundle : bundles) {
            for (String classEntry : bundle.getClassEntries()) {
                Optional<String> className = detectResourceOrProvider(bundle.classLoader, classEntry);
                className.ifPresent(cname -> ret.add(loadClass(bundle.symbolicName, bundle.classLoader, cname)));
            }
        }
        return ret;
    }

    private static Optional<String> detectResourceOrProvider(ClassLoader bundleClassLoader, String classEntry) {
        try (InputStream inputStream = getResourceAsStream(bundleClassLoader, classEntry)) {
            ResourceOrProviderClassVisitor visitor = ResourceOrProviderClassVisitor.visit(new ClassReader(inputStream));
            return Optional.ofNullable(visitor.getClassName());
        } catch (IOException e) {
            // ignored
        }
        return Optional.empty();
    }

    private static InputStream getResourceAsStream(ClassLoader bundleClassLoader, String classEntry) {
        InputStream is = bundleClassLoader.getResourceAsStream(classEntry);
        if (is == null) {
            throw new RuntimeException("No entry " + classEntry + " in bundle " + bundleClassLoader);
        } else {
            return is;
        }
    }

    private static Class<?> loadClass(String bundleSymbolicName, ClassLoader classLoader, String className) {
        try {
            return classLoader.loadClass(className);
        } catch (Exception e) {
            throw new RuntimeException("Failed loading class " + className + " from bundle " + bundleSymbolicName, e);
        }
    }

    private static Binder componentInjectorBinder(RestApiContext restApiContext) {
        final ComponentGraphProvider componentGraphProvider = new ComponentGraphProvider(restApiContext.getInjectableComponents());
        final TypeLiteral<InjectionResolver<Component>> componentAnnotationType = new TypeLiteral<InjectionResolver<Component>>() {
        };

        return new AbstractBinder() {
            @Override
            public void configure() {
                bind(componentGraphProvider).to(componentAnnotationType);
            }
        };
    }

    private static JacksonJaxbJsonProvider jacksonDatatypeJdk8Provider() {
        JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
        provider.setMapper(new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule()));
        return provider;
    }

    @Override
    public ServletHolder get() {
        return jerseyServletHolder;
    }

    @Override
    public void deconstruct() {
    }
}
