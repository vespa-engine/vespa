// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.di.config.JerseyBundlesConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.vespa.model.container.jersey.JerseyHandler;
import com.yahoo.vespa.model.container.jersey.RestApi;
import com.yahoo.vespa.model.container.jersey.RestApiContext;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class MultipleRestApisTest extends ContainerModelBuilderTestBase {

    private static final String CLUSTER_ID = "container";
    private static final String PATH_1 = "rest_1";
    private static final String PATH_2 = "rest_2";
    private static final String HTTP_BINDING_1 = "http://*/" + PATH_1 + "/*";
    private static final String HTTPS_BINDING_1 = "https://*/" + PATH_1 + "/*";
    private static final String HTTP_BINDING_2 = "http://*/" + PATH_2 + "/*";
    private static final String HTTPS_BINDING_2 = "https://*/" + PATH_2 + "/*";
    private static final String HANDLER_ID_1 = JerseyHandler.CLASS + "-" + PATH_1;
    private static final String HANDLER_ID_2 = JerseyHandler.CLASS + "-" + PATH_2;
    private static final String REST_API_CONTEXT_ID_1 = RestApiContext.CONTAINER_CLASS + "-" + PATH_1;
    private static final String REST_API_CONTEXT_ID_2 = RestApiContext.CONTAINER_CLASS + "-" + PATH_2;
    private static final String REST_API_XML =
            "<container version=\"1.0\" id=\"" + CLUSTER_ID + "\">\n" +
            "  <rest-api path=\"" + PATH_1 + "\">\n" +
            "    <components bundle=\"bundle1\" />\n" +
            "  </rest-api>\n" +
            "  <rest-api path=\"" + PATH_2 + "\">\n" +
            "    <components bundle=\"bundle2\" />\n" +
            "  </rest-api>\n" +
            "</container>";


    private JerseyHandler handler1;
    private JerseyHandler handler2;
    private Map<ComponentId, RestApi> restApis;

    @Before
    public void setup() throws Exception {
        createModel(root, DomBuilderTest.parse(REST_API_XML));
        handler1 = (JerseyHandler)getContainerComponentNested(CLUSTER_ID, HANDLER_ID_1);
        handler2 = (JerseyHandler)getContainerComponentNested(CLUSTER_ID, HANDLER_ID_2);
        restApis = getContainerCluster(CLUSTER_ID).getRestApiMap();
    }

    @Test
    public void cluster_has_all_rest_apis() {
        assertThat(restApis.size(), is(2));
    }

    @Test
    public void rest_apis_have_path_as_component_id() {
        assertTrue(restApis.get(ComponentId.fromString(PATH_1)) instanceof RestApi);
        assertTrue(restApis.get(ComponentId.fromString(PATH_2)) instanceof RestApi);
    }

    @Test
    public void jersey_handler_has_correct_bindings() {
        assertThat(handler1, not(nullValue()));
        assertThat(handler1.getServerBindings(), hasItems(HTTP_BINDING_1, HTTPS_BINDING_1));

        assertThat(handler2, not(nullValue()));
        assertThat(handler2.getServerBindings(), hasItems(HTTP_BINDING_2, HTTPS_BINDING_2));
    }

    @Test
    public void jersey_bindings_are_included_in_config() {
        JdiscBindingsConfig config = root.getConfig(JdiscBindingsConfig.class, CLUSTER_ID);
        assertThat(config.handlers(HANDLER_ID_1).serverBindings(), hasItems(HTTP_BINDING_1, HTTPS_BINDING_1));
        assertThat(config.handlers(HANDLER_ID_2).serverBindings(), hasItems(HTTP_BINDING_2, HTTPS_BINDING_2));
    }


    @Test
    public void jersey_handler_for_each_rest_api_is_included_in_components_config() {
        ComponentsConfig config = root.getConfig(ComponentsConfig.class, CLUSTER_ID);
        assertThat(config.toString(), containsString(".id \"" + HANDLER_ID_1 + "\""));
        assertThat(config.toString(), containsString(".id \"" + HANDLER_ID_2 + "\""));
    }

    @Test
    public void jersey_bundles_component_for_each_rest_api_is_included_in_components_config() {

        ComponentsConfig config = root.getConfig(ComponentsConfig.class, CLUSTER_ID);
        assertThat(config.toString(), containsString(".id \"" + REST_API_CONTEXT_ID_1 + "\""));
        assertThat(config.toString(), containsString(".id \"" + REST_API_CONTEXT_ID_2 + "\""));
    }

    @Test
    public void each_rest_api_has_correct_bundle() {
        RestApiContext restApiContext1 = restApis.get(ComponentId.fromString(PATH_1)).getContext();
        RestApiContext restApiContext2 = restApis.get(ComponentId.fromString(PATH_2)).getContext();

        JerseyBundlesConfig bundlesConfig1 = root.getConfig(JerseyBundlesConfig.class, restApiContext1.getConfigId());
        assertThat(bundlesConfig1.toString(), containsString("bundle1"));
        assertThat(bundlesConfig1.toString(), not(containsString("bundle2")));

        JerseyBundlesConfig bundlesConfig2 = root.getConfig(JerseyBundlesConfig.class, restApiContext2.getConfigId());
        assertThat(bundlesConfig2.toString(), containsString("bundle2"));
        assertThat(bundlesConfig2.toString(), not(containsString("bundle1")));
    }
}
