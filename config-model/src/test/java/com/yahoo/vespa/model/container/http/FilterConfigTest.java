// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.core.http.HttpFilterConfig;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static com.yahoo.collections.CollectionUtil.first;
import static com.yahoo.vespa.model.container.http.FilterConfigProvider.configProviderId;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 * @since 5.1.23
 */
public class FilterConfigTest extends DomBuilderTest {

    private Http http;

    @BeforeEach
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
    void filter_without_config_does_not_have_FilterConfigProvider() {
        Filter noConfigFilter = getOuterFilter("no-config");

        assertNull(getProvider(noConfigFilter));
    }

    @Test
    void filterName_is_id_from_component_spec() {
        Filter emptyConfigFilter = getOuterFilter("empty-config");
        HttpFilterConfig config = getHttpFilterConfig(emptyConfigFilter);

        assertEquals("empty-config", config.filterName());
    }

    @Test
    void filterClass_is_class_from_component_spec() {
        Filter emptyConfigFilter = getOuterFilter("empty-config");
        HttpFilterConfig config = getHttpFilterConfig(emptyConfigFilter);

        assertEquals("EmptyConfigFilter", config.filterClass());
    }

    @Test
    void filter_with_empty_config_has_FilterConfigProvider_with_empty_map() {
        Filter emptyConfigFilter = getOuterFilter("empty-config");
        HttpFilterConfig config = getHttpFilterConfig(emptyConfigFilter);

        assertTrue(config.param().isEmpty());
    }

    @Test
    void config_params_are_set_correctly_in_FilterConfigProvider() {
        Filter configWithParamsFilter = getOuterFilter("config-with-params");
        HttpFilterConfig config = getHttpFilterConfig(configWithParamsFilter);

        assertEquals(1, config.param().size());
        assertEquals("key1", config.param(0).name());
        assertEquals("value1", config.param(0).value());
    }

    @Test
    void inner_filter_can_have_filter_config() {
        Filter innerFilter =
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
