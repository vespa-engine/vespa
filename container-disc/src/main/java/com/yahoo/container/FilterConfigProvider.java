// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container;

import com.yahoo.container.core.http.HttpFilterConfig;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.http.filter.FilterConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tony Vaagenes
 */
public final class FilterConfigProvider implements Provider<FilterConfig> {

    private static class MapFilterConfig implements FilterConfig {

        private final Map<String, String> initParameters;
        private final String filterName;
        private final String filterClass;

        MapFilterConfig(Map<String, String> initParameters, String filterName, String filterClass) {
            this.initParameters = initParameters;
            this.filterName = filterName;
            this.filterClass = filterClass;
        }

        @Override
        public String getFilterName() {
            return filterName;
        }

        @Override
        public String getFilterClass() {
            return filterClass;
        }

        @Override
        public String getInitParameter(String name) {
            return initParameters.get(name);
        }

        @Override
        public boolean getBooleanInitParameter(String name, boolean defaultValue) {
            return initParameters.containsKey(name) ?
                    Boolean.parseBoolean(initParameters.get(name)) :
                    defaultValue;
        }

        @Override
        public Collection<String> getInitParameterNames() {
            return initParameters.keySet();
        }
    }

    private final FilterConfig filterConfig;

    public FilterConfigProvider(HttpFilterConfig vespaConfig) {
        filterConfig = new MapFilterConfig(toMap(vespaConfig), vespaConfig.filterName(), vespaConfig.filterClass());
    }

    private static Map<String, String> toMap(HttpFilterConfig vespaConfig) {
        Map<String, String> parameters = new HashMap<>();
        for (HttpFilterConfig.Param param : vespaConfig.param())
            parameters.put(param.name(), param.value());
        return parameters;
    }

    @Override
    public FilterConfig get() {
        return filterConfig;
    }

    @Override
    public void deconstruct() {}

}
