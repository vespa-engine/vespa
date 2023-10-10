// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.core.http.HttpFilterConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.yahoo.container.core.http.HttpFilterConfig.Param;

/**
 * @author gjoranv
 * @since 5.1.23
 */
public class FilterConfigProvider extends SimpleComponent implements HttpFilterConfig.Producer {

    private static final ComponentSpecification filterConfigProviderClass =
            ComponentSpecification.fromString(com.yahoo.container.FilterConfigProvider.class.getName());

    private final ChainedComponentModel filterModel;
    private HashMap<String, String> configMap = new LinkedHashMap<>();

    public FilterConfigProvider(ChainedComponentModel filterModel) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(
                        configProviderId(filterModel.getComponentId()),
                        filterConfigProviderClass,
                        null)));

        this.filterModel = filterModel;
    }

    @Override
    public void getConfig(HttpFilterConfig.Builder builder) {
        builder.filterName(filterModel.getComponentId().stringValue())
                .filterClass(filterModel.getClassId().stringValue());

        for (Map.Entry<String, String> param : configMap.entrySet()) {
            builder.param(
                    new Param.Builder()
                            .name(param.getKey())
                            .value(param.getValue()));
        }
    }

    public String putConfig(String key, String value) {
        return configMap.put(key, value);
    }

    static ComponentId configProviderId(ComponentId filterId) {
        return ComponentId.fromString("filterConfig").nestInNamespace(filterId);
    }

}
