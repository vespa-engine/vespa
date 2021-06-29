// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.test.TestUtil;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.di.config.JerseyBundlesConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.jersey.Jersey2Servlet;
import com.yahoo.vespa.model.container.jersey.RestApi;
import com.yahoo.vespa.model.container.jersey.RestApiContext;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTestBase;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author gjoranv
 * @author bjorncs
 */
@Ignore // TODO: remove test
public class RestApiTest extends ContainerModelBuilderTestBase {
    private static final String PATH = "rest/api";
    private static final String REST_API_CONTEXT_ID = RestApiContext.CONTAINER_CLASS + "-" + RestApi.idFromPath(PATH);
    private static final String INJECTED_COMPONENT_ID = "injectedHandler";
    private static final String CLUSTER_ID = "container";

    private static final Element restApiXml = TestUtil.parse(
            "<container version=\"1.0\" id=\"" + CLUSTER_ID + "\">",
            "  <rest-api path=\"" + PATH + "\">",
            "    <components bundle=\"my-jersey-bundle:1.0\">",
            "      <package>com.yahoo.foo</package>",
            "    </components>",
            "  </rest-api>",
            "  <handler id=\"" + INJECTED_COMPONENT_ID + "\" />",
            "</container>");

    private RestApi restApi;
    private Jersey2Servlet servlet;
    private RestApiContext context;

    @Before
    public void setup() throws Exception {
        createModel(root, restApiXml);
        root.validate();
        getContainerCluster(CLUSTER_ID).prepare(root.getDeployState());
        restApi = getContainerCluster(CLUSTER_ID).getRestApiMap().values().iterator().next();
        servlet = restApi.getJersey2Servlet();
        context = restApi.getContext();
    }

    @Test
    public void jersey2_servlet_has_correct_binding_path() {
        assertThat(servlet, not(nullValue()));
        assertThat(servlet.bindingPath, is(PATH + "/*"));
    }

    @Test
    public void jersey2_servlet_has_correct_bundle_spec() {
        assertThat(servlet.model.bundleInstantiationSpec.bundle.stringValue(), is(Jersey2Servlet.BUNDLE));
    }

    @Test
    public void rest_api_path_is_included_in_servlet_config() {
        ServletPathsConfig config = root.getConfig(ServletPathsConfig.class, servlet.getConfigId());
        assertThat(config.servlets(servlet.getComponentId().stringValue()).path(), is(PATH + "/*"));
    }

    @Test
    public void resource_bundles_are_included_in_config() {
        JerseyBundlesConfig config = root.getConfig(JerseyBundlesConfig.class, context.getConfigId());
        assertThat(config.bundles().size(), is(1));
        assertThat(config.bundles(0).spec(), is("my-jersey-bundle:1.0"));
    }

    @Test
    public void packages_to_scan_are_included_in_config() {
        JerseyBundlesConfig config = root.getConfig(JerseyBundlesConfig.class, context.getConfigId());
        assertThat(config.bundles(0).packages(), contains("com.yahoo.foo"));
    }

    @Test
    public void jersey2_servlet_is_included_in_components_config() {
        ComponentsConfig config = root.getConfig(ComponentsConfig.class, CLUSTER_ID);
        assertThat(config.toString(), containsString(".id \"" + servlet.getComponentId().stringValue() + "\""));
    }

    @Test
    public void restApiContext_is_included_in_components_config() {
        ComponentsConfig config = root.getConfig(ComponentsConfig.class, CLUSTER_ID);
        assertThat(config.toString(), containsString(".id \"" + REST_API_CONTEXT_ID + "\""));
    }

    @Test
    public void all_non_restApi_components_are_injected_to_RestApiContext() {
        ComponentsConfig componentsConfig = root.getConfig(ComponentsConfig.class, CLUSTER_ID);

        Set<ComponentId> clusterChildrenComponentIds = getContainerCluster(CLUSTER_ID).getAllComponents().stream()
                .map(Component::getComponentId)
                .collect(Collectors.toSet());

        Set<ComponentId> restApiChildrenComponentIds = restApi.getChildren().values().stream()
                .map(child -> ((Component<?, ?>) child).getComponentId())
                .collect(Collectors.toSet());

        //TODO: try replacing with filtering against RestApiContext.isCycleGeneratingComponent
        ComponentId cycleInducingComponents = ComponentId.fromString("com.yahoo.container.handler.observability.ApplicationStatusHandler");

        Set<ComponentId> expectedInjectedConfigIds = new HashSet<>(clusterChildrenComponentIds);
        expectedInjectedConfigIds.removeAll(restApiChildrenComponentIds);
        expectedInjectedConfigIds.remove(cycleInducingComponents);

        Set<ComponentId> injectedConfigIds = restApiContextConfig(componentsConfig).inject().stream()
                .map(inject -> ComponentId.fromString(inject.id()))
                .collect(Collectors.toSet());

        // Verify that the two sets are equal. Split in two asserts to get decent failure messages.
        assertThat(
                "Not all required components are injected",
                injectedConfigIds,
                containsInAnyOrder(expectedInjectedConfigIds.toArray()));
        assertThat(
                "We inject some components that should not be injected",
                expectedInjectedConfigIds,
                containsInAnyOrder(injectedConfigIds.toArray()));
    }

    private static ComponentsConfig.Components restApiContextConfig(ComponentsConfig config) {
        return config.components().stream()
                .filter(component -> component.classId().equals(RestApiContext.CONTAINER_CLASS))
                .findFirst()
                .get();
    }

}
