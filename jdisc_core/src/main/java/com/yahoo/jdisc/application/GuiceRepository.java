// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.common.collect.ImmutableSet;
import com.google.inject.*;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.yahoo.jdisc.Container;
import org.osgi.framework.Bundle;

import java.util.*;
import java.util.logging.Logger;

/**
 * This is a repository of {@link Module}s. An instance of this class is owned by the {@link ContainerBuilder}, and is
 * used to configure the set of Modules that eventually form the {@link Injector} of the active {@link Container}.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class GuiceRepository implements Iterable<Module> {

    private static final Logger log = Logger.getLogger(GuiceRepository.class.getName());
    private final Map<Module, List<Element>> modules = new LinkedHashMap<>();
    private Injector injector;

    public GuiceRepository(Module... modules) {
        installAll(Arrays.asList(modules));
    }

    public Injector activate() {
        return getInjector();
    }

    public List<Module> installAll(Bundle bundle, Iterable<String> moduleNames) throws ClassNotFoundException {
        List<Module> lst = new LinkedList<>();
        for (String moduleName : moduleNames) {
            lst.add(install(bundle, moduleName));
        }
        return lst;
    }

    public Module install(Bundle bundle, String moduleName) throws ClassNotFoundException {
        log.finer("Installing Guice module '" + moduleName + "'.");
        Class<?> namedClass = bundle.loadClass(moduleName);
        Class<Module> moduleClass = ContainerBuilder.safeClassCast(Module.class, namedClass);
        Module module = getInstance(moduleClass);
        install(module);
        return module;
    }

    public void installAll(Iterable<? extends Module> modules) {
        for (Module module : modules) {
            install(module);
        }
    }

    public void install(Module module) {
        modules.put(module, Elements.getElements(module));
        injector = null;
    }

    public void uninstallAll(Iterable<? extends Module> modules) {
        for (Module module : modules) {
            uninstall(module);
        }
    }

    public void uninstall(Module module) {
        modules.remove(module);
        injector = null;
    }

    public Injector getInjector() {
        if (injector == null) {
            injector = Guice.createInjector(createModule());
        }
        return injector;
    }

    public <T> T getInstance(Key<T> key) {
        return getInjector().getInstance(key);
    }

    public <T> T getInstance(Class<T> type) {
        return getInjector().getInstance(type);
    }

    public Collection<Module> collection() { return ImmutableSet.copyOf(modules.keySet()); }

    @Override
    public Iterator<Module> iterator() {
        return collection().iterator();
    }

    private Module createModule() {
        List<Element> allElements = new LinkedList<>();
        for (List<Element> moduleElements : modules.values()) {
            allElements.addAll(moduleElements);
        }
        ElementCollector collector = new ElementCollector();
        for (ListIterator<Element> it = allElements.listIterator(allElements.size()); it.hasPrevious(); ) {
            it.previous().acceptVisitor(collector);
        }
        return Elements.getModule(collector.elements);
    }

    private static class ElementCollector extends DefaultElementVisitor<Boolean> {

        final Set<Key<?>> seenKeys = new HashSet<>();
        final List<Element> elements = new LinkedList<>();

        @Override
        public <T> Boolean visit(Binding<T> binding) {
            if (seenKeys.add(binding.getKey())) {
                elements.add(binding);
            }
            return Boolean.TRUE;
        }

        @Override
        public Boolean visitOther(Element element) {
            elements.add(element);
            return Boolean.TRUE;
        }
    }
}
