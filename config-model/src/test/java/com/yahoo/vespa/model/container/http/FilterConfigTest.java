// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.core.http.HttpFilterConfig;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import static com.yahoo.collections.CollectionUtil.first;
import static com.yahoo.vespa.model.container.http.FilterConfigProvider.configProviderId;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 * @since 5.1.23
 */
public class FilterConfigTest extends DomBuilderTest {

    private Http http;

    @Before
    public void setupFilterChains() {
        http = new HttpBuilder().build(root.getDeployState(), root, servicesXml());
        root.freezeModelTopology();
    }

    private Element servicesXml() {
        return parse(
                "<http>",
                "  <filtering>",
                "    <filter id='no-config' />",

                "    <filter id='empty-config' class='EmptyConfigFilter'>",
                "      <filter-config />",
                "    </filter>",

                "    <filter id='config-with-params'>",
                "      <filter-config>",
                "        <key1>value1</key1>",
                "      </filter-config>",
                "    </filter>",

                "    <request-chain id='myChain'>",
                "      <filter id='inner-with-empty-config'>",
                "        <filter-config />",
                "      </filter>",
                "    </request-chain>",
                "  </filtering>",
                "</http>");
    }

    @Test
    public void filter_without_config_does_not_have_FilterConfigProvider() {
        Filter noConfigFilter = getOuterFilter("no-config");

        assertThat(getProvider(noConfigFilter), nullValue());
    }

    @Test
    public void filterName_is_id_from_component_spec() {
        Filter emptyConfigFilter = getOuterFilter("empty-config");
        HttpFilterConfig config = getHttpFilterConfig(emptyConfigFilter);

        assertThat(config.filterName(), is("empty-config"));
    }

    @Test
    public void filterClass_is_class_from_component_spec() {
        Filter emptyConfigFilter = getOuterFilter("empty-config");
        HttpFilterConfig config = getHttpFilterConfig(emptyConfigFilter);

        assertThat(config.filterClass(), is("EmptyConfigFilter"));
    }

    @Test
    public void filter_with_empty_config_has_FilterConfigProvider_with_empty_map() {
        Filter emptyConfigFilter = getOuterFilter("empty-config");
        HttpFilterConfig config = getHttpFilterConfig(emptyConfigFilter);

        assertThat(config.param(), is(empty()));
    }

    @Test
    public void config_params_are_set_correctly_in_FilterConfigProvider() {
        Filter configWithParamsFilter = getOuterFilter("config-with-params");
        HttpFilterConfig config = getHttpFilterConfig(configWithParamsFilter);

        assertThat(config.param(), hasSize(1));
        assertThat(config.param(0).name(), is("key1"));
        assertThat(config.param(0).value(), is("value1"));
    }

    @Test
    public void inner_filter_can_have_filter_config() {
        Filter innerFilter = (Filter)
                first(http.getFilterChains().allChains().getComponent("myChain").getInnerComponents());

        getHttpFilterConfig(innerFilter);
    }

    private Filter getOuterFilter(String id) {
        return (Filter)http.getFilterChains().componentsRegistry().getComponent(id);
    }

    private static HttpFilterConfig getHttpFilterConfig(Filter filter) {
        FilterConfigProvider configProvider = getProvider(filter);

        HttpFilterConfig.Builder builder = new HttpFilterConfig.Builder();
        configProvider.getConfig(builder);
        return new HttpFilterConfig(builder);
    }

    static FilterConfigProvider getProvider(Filter filter) {
        String providerId = configProviderId(filter.getComponentId()).stringValue();
        return (FilterConfigProvider)filter.getChildren().get(providerId);
    }

}
