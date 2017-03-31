// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.config.jersey.JerseyInitConfig;
import com.yahoo.container.di.config.JerseyBundlesConfig;
import com.yahoo.container.di.config.JerseyInjectionConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.jersey.JerseyHandler;
import com.yahoo.vespa.model.container.jersey.RestApi;
import com.yahoo.vespa.model.container.jersey.RestApiContext;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilderTestBase;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author bjorncs
 */
public class RestApiTest extends ContainerModelBuilderTestBase {
    private static final String Path = "rest/api";
    private static final String HttpBinding = "http://*/" + Path + "/*";
    private static final String HttpsBinding = "https://*/" + Path + "/*";
    private static final String HandlerId = JerseyHandler.CLASS + "-" + RestApi.idFromPath(Path);
    private static final String RestApiContextId = RestApiContext.CONTAINER_CLASS + "-" + RestApi.idFromPath(Path);
    private static final String InjectedComponentId = "injectedHandler";

    private static final String ClusterId = "container";

    private static final String restApiXml =
            "<container version=\"1.0\" id=\"" + ClusterId + "\" jetty=\"true\">\n" +
            "  <rest-api path=\"" + Path + "\">\n" +
            "    <components bundle=\"my-jersey-bundle:1.0\">\n" +
            "      <package>com.yahoo.foo</package>\n" +
            "    </components>\n" +
            "  </rest-api>\n" +
            "  <handler id=\"" + InjectedComponentId + "\" />\n" +
            "</container>";

    private RestApi restApi;
    private JerseyHandler handler;
    private RestApiContext context;

    public void setup() throws Exception {
        createModel(root, DomBuilderTest.parse(restApiXml));
        root.validate();
        getContainerCluster(ClusterId).prepare();
        restApi = getContainerCluster(ClusterId).getRestApiMap().values().iterator().next();
        handler = (JerseyHandler) getContainerComponentNested(ClusterId, HandlerId);
        context = restApi.getContext();
    }

    @Test
    public void jersey_handler_has_correct_bindings() throws Exception {
        setup();
        assertThat(handler, not(nullValue()));
        assertThat(handler.getServerBindings(), hasItems(HttpBinding, HttpsBinding));
    }

    @Test
    public void jersey_bindings_are_included_in_config() throws Exception {
        setup();
        JdiscBindingsConfig config = root.getConfig(JdiscBindingsConfig.class, ClusterId);
        assertThat(config.handlers(HandlerId).serverBindings(), hasItems(HttpBinding, HttpsBinding));
    }

    @Test
    public void jersey_handler_has_correct_bundle_spec() throws Exception {
        setup();
        assertThat(handler.model.bundleInstantiationSpec.bundle.stringValue(), is(JerseyHandler.BUNDLE));
    }

    @Test
    public void config_has_correct_jersey_mapping() throws Exception {
        setup();
        JerseyInitConfig config = root.getConfig(JerseyInitConfig.class, handler.getConfigId());
        assertThat(config.jerseyMapping(), is(Path));
    }

    @Test
    public void resource_bundles_are_included_in_config() throws Exception {
        setup();
        JerseyBundlesConfig config = root.getConfig(JerseyBundlesConfig.class, context.getConfigId());
        assertThat(config.bundles().size(), is(1));
        assertThat(config.bundles(0).spec(), is("my-jersey-bundle:1.0"));
    }

    @Test
    public void packages_to_scan_are_included_in_config() throws Exception {
        setup();
        JerseyBundlesConfig config = root.getConfig(JerseyBundlesConfig.class, context.getConfigId());
        assertThat(config.bundles(0).packages(), contains("com.yahoo.foo"));
    }

    @Test
    public void jersey_handler_is_included_in_components_config() throws Exception {
        setup();
        ComponentsConfig config = root.getConfig(ComponentsConfig.class, ClusterId);
        assertThat(config.toString(), containsString(".id \"" + HandlerId + "\""));
    }

    @Test
    public void restApiContext_is_included_in_components_config() throws Exception {
        setup();
        ComponentsConfig config = root.getConfig(ComponentsConfig.class, ClusterId);
        assertThat(config.toString(), containsString(".id \"" + RestApiContextId + "\""));
    }

    @Test
    public void all_non_restApi_components_are_injected_to_RestApiContext() throws Exception {
        setup();
        ComponentsConfig componentsConfig = root.getConfig(ComponentsConfig.class, ClusterId);

        Set<ComponentId> clusterChildrenComponentIds = getContainerCluster(ClusterId).getAllComponents().stream()
                .map(Component::getComponentId)
                .collect(Collectors.toSet());

        Set<ComponentId> restApiChildrenComponentIds = restApi.getChildren().values().stream()
                .map(child -> ((Component<?, ?>) child).getComponentId())
                .collect(Collectors.toSet());

        //TODO: Review: replace with filtering against RestApiContext.isCycleGeneratingComponent
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

    @Ignore // TODO: use for naming components instead
    @Test
    public void jdisc_components_can_be_injected() throws Exception {
        setup();
        JerseyInjectionConfig config = root.getConfig(JerseyInjectionConfig.class, context.getConfigId());
        assertThat(config.inject(0).instance(), is("injectedHandler"));
        assertThat(config.inject(0).forClass(), is("com.yahoo.handler.Handler"));
    }

    @Ignore // TODO: use for naming a non-existent component instead
    @Test(expected = IllegalArgumentException.class)
    public void injecting_non_existent_component() throws Exception {
        String restApiXml =
                "<container version=\"1.0\" id=\"" + ClusterId + "\">\n" +
                "  <rest-api path=\"" + Path + "\">\n" +
                "    <components bundle=\"my-jersey-bundle:1.0\" />\n" +
                "    <inject jdisc-component=\"non-existent\" for-class=\"foo\" />\n" +
                "  </rest-api>\n" +
                "</container>";
        createModel(root, DomBuilderTest.parse(restApiXml));
        root.validate();
    }

    @Test
    public void legacy_syntax_should_produce_valid_model() throws Exception {
        String legacyXml =
                "<container version=\"1.0\" >\n" +
                "  <handler id=\"" + JerseyHandler.CLASS + "\" >\n" +
                "    <binding>" + HttpBinding + "</binding>\n" +
                "    <config name=\"jdisc.jersey.jersey-handler\">\n" +
                "      <jerseyMapping>jersey</jerseyMapping>\n" +
                "    </config>\n" +
                "  </handler>\n" +
                "</container>";

        createModel(root, DomBuilderTest.parse(legacyXml));

        Handler<?> handler = (Handler<?>) getContainerComponent("container", JerseyHandler.CLASS);
        assertThat(handler, not(nullValue()));
        assertThat(handler.getServerBindings(), hasItem(HttpBinding));

        JdiscBindingsConfig bindingsConfig = root.getConfig(JdiscBindingsConfig.class, ClusterId);
        assertThat(bindingsConfig.handlers(JerseyHandler.CLASS).serverBindings(), hasItem(HttpBinding));
    }

}
