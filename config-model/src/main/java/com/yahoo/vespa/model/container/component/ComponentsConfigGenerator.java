// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.collections.Pair;
import com.yahoo.container.bundle.BundleInstantiationSpecification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.yahoo.container.ComponentsConfig.Components;

/**
 * @author gjoranv
 */
public class ComponentsConfigGenerator {

    public static List<Components.Builder> generate(Collection<? extends Component<?, ?>> components) {
        List<Components.Builder> result = new ArrayList<>();

        for (Component component : components) {
            result.add(componentsConfig(component));
        }
        return result;
    }

    public static Components.Builder componentsConfig(Component<?, ?> component) {
        Components.Builder builder = new Components.Builder();
        builder.id(component.getGlobalComponentId().stringValue());
        builder.configId(component.getConfigId());


        bundleInstantiationSpecification(builder, component.model.bundleInstantiationSpec);
        builder.inject.addAll(componentsToInject(component.injectedComponents));

        return builder;
    }

    private static void bundleInstantiationSpecification(Components.Builder config, BundleInstantiationSpecification spec) {
        config.classId(spec.classId.stringValue());
        config.bundle(spec.bundle.stringValue());
    }


    private static List<Components.Inject.Builder> componentsToInject(
            Collection<Pair<String, Component>> injectedComponents) {

        List<Components.Inject.Builder> result = new ArrayList<>();

        for (Pair<String, Component> injected : injectedComponents) {
            Components.Inject.Builder builder = new Components.Inject.Builder();

            builder.id(injected.getSecond().getGlobalComponentId().stringValue());
            builder.name(injected.getFirst());
            result.add(builder);
        }
        return result;
    }
}
