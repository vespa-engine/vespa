package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.usability.BindingsOverviewHandler;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.model.container.ContainerCluster.ROOT_HANDLER_BINDING;
import static com.yahoo.vespa.model.container.ContainerCluster.STATE_HANDLER_BINDING_1;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for container model building with custom handlers.
 *
 * @author gjoranv
 */
public class HandlerBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    void handlers_are_included_in_components_config() {
        createClusterWithJDiscHandler();
        assertThat(componentsConfig().toString(), containsString(".id \"discHandler\""));
    }

    @Test
    void handler_bindings_are_included_in_discBindings_config() {
        createClusterWithJDiscHandler();
        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertThat(discBindingsConfig, containsString(".serverBindings[0] \"http://*/binding0\""));
        assertThat(discBindingsConfig, containsString(".serverBindings[1] \"http://*/binding1\""));
    }

    @Test
    void nested_components_are_injected_to_handlers() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <handler id='myHandler'>",
                "    <component id='injected' />",
                "  </handler>",
                "</container>");

        createModel(root, clusterElem);
        Component<?, ?> handler = getComponent("default", "myHandler");
        assertThat(handler.getInjectedComponentIds(), hasItem("injected@myHandler"));
    }

    @Test
    void default_root_handler_binding_can_be_stolen_by_user_configured_handler() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                        "  <handler id='userRootHandler'>" +
                        "    <binding>" + ROOT_HANDLER_BINDING.patternString() + "</binding>" +
                        "  </handler>" +
                        "</container>");
        createModel(root, clusterElem);

        // The handler is still set up.
        ComponentsConfig.Components userRootHandler = getComponentInConfig(componentsConfig(), BindingsOverviewHandler.class.getName());
        assertNotNull(userRootHandler);

        // .. but it has no bindings
        var discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");
        assertNull(discBindingsConfig.handlers(BindingsOverviewHandler.class.getName()));
    }

    @Test
    void reserved_binding_cannot_be_stolen_by_user_configured_handler() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>" +
                        "  <handler id='userHandler'>" +
                        "    <binding>" + STATE_HANDLER_BINDING_1.patternString() + "</binding>" +
                        "  </handler>" +
                        "</container>");
        try {
            createModel(root, clusterElem);
            fail("Expected exception when stealing a reserved binding.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Binding 'http://*/state/v1' is a reserved Vespa binding " +
                    "and cannot be used by handler: userHandler"));
        }
    }

    @Test
    void custom_handler_gets_default_threadpool() {
        createClusterWithJDiscHandler();
        ApplicationContainerCluster cluster = (ApplicationContainerCluster) root.getChildren().get("default");
        Handler handler = cluster.getHandlers().stream()
                .filter(h -> h.getComponentId().toString().equals("discHandler"))
                .findAny().orElseThrow();

        assertTrue(handler.getInjectedComponentIds().contains("threadpool@default-handler-common"));
    }

    @Test
    void restricts_default_bindings_in_hosted_vespa() {
        DeployState deployState = new DeployState.Builder()
                .properties(new TestProperties().setHostedVespa(true).setUseRestrictedDataPlaneBindings(true))
                .build();
        verifyDefaultBindings(deployState, "http://*:4443");
    }

    @Test
    void does_not_restrict_default_bindings_in_hosted_vespa_when_disabled() {
        DeployState deployState = new DeployState.Builder()
                .properties(new TestProperties().setHostedVespa(true).setUseRestrictedDataPlaneBindings(false))
                .build();
        verifyDefaultBindings(deployState, "http://*");
    }

    @Test
    void does_not_restrict_infrastructure() {
        DeployState deployState = new DeployState.Builder()

                .properties(
                        new TestProperties()
                                .setApplicationId(ApplicationId.defaultId())
                                .setHostedVespa(true)
                                .setUseRestrictedDataPlaneBindings(false))
                .build();
        verifyDefaultBindings(deployState, "http://*");
    }

    @Test
    void restricts_custom_bindings_in_hosted_vespa() {
        DeployState deployState = new DeployState.Builder()
                .properties(new TestProperties().setHostedVespa(true).setUseRestrictedDataPlaneBindings(true))
                .build();
        verifyCustomSearchBindings(deployState, "http://*:4443");
    }

    @Test
    void does_not_restrict_default_bindings_in_self_hosted() {
        DeployState deployState = new DeployState.Builder()
                .properties(new TestProperties().setHostedVespa(false).setUseRestrictedDataPlaneBindings(false))
                .build();
        verifyDefaultBindings(deployState, "http://*");
    }

    @Test
    void does_not_restrict_custom_bindings_in_self_hosted() {
        DeployState deployState = new DeployState.Builder()
                .properties(new TestProperties().setHostedVespa(false).setUseRestrictedDataPlaneBindings(false))
                .build();
        verifyCustomSearchBindings(deployState, "http://*");
    }

    private void verifyDefaultBindings(DeployState deployState, String bindingPrefix) {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search/>",
                "  <document-api/>",
                "  <handler id='FooHandler'>",
                "     <binding>http://*/foo</binding>",
                "  </handler>",
                nodesXml,
                "</container>");

        createModel(root, deployState, null, clusterElem);
        JdiscBindingsConfig bindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");

        // Verify /search /feed /document and custom handler are bound correctly
        Map<String, JdiscBindingsConfig.Handlers> handlers = bindingsConfig.handlers();
        Map<String, List<String>> expectedHandlerMappings = Map.of(
                "com.yahoo.search.handler.SearchHandler", List.of("/search/*"),
                "com.yahoo.document.restapi.resource.DocumentV1ApiHandler", List.of("/document/v1/*", "/document/v1/*/"),
                "com.yahoo.vespa.http.server.FeedHandler", List.of("/reserved-for-internal-use/feedapi", "/reserved-for-internal-use/feedapi/"),
                "FooHandler", List.of("/foo"));
        expectedHandlerMappings.forEach((handler, bindings) -> validateHandler(handlers.get(handler), bindingPrefix, bindings));

        // All other handlers should be bound to default (http://*/...)
        handlers.entrySet().stream()
                .filter(e -> ! expectedHandlerMappings.containsKey(e.getKey()))
                .forEach(e -> assertTrue(e.getValue().serverBindings().stream().allMatch(s -> s.startsWith("http://*/"))));
    }

    private void verifyCustomSearchBindings(DeployState deployState, String bindingPrefix) {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search>",
                "     <binding>http://*/search-binding/</binding>",
                "  </search>",
                "  <document-api>",
                "     <binding>http://*/docapi-binding/</binding>",
                "  </document-api>",
                nodesXml,
                "</container>");

        createModel(root, deployState, null, clusterElem);
        JdiscBindingsConfig bindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");

        // Verify search feed and document handler are bound correctly
        Map<String, JdiscBindingsConfig.Handlers> handlers = bindingsConfig.handlers();
        Map<String, List<String>> expectedHandlerMappings = Map.of(
                "com.yahoo.search.handler.SearchHandler", List.of("/search-binding/"),
                "com.yahoo.document.restapi.resource.DocumentV1ApiHandler", List.of("/docapi-binding/document/v1/*", "/docapi-binding/document/v1/*/"),
                "com.yahoo.vespa.http.server.FeedHandler", List.of("/docapi-binding/reserved-for-internal-use/feedapi", "/docapi-binding/reserved-for-internal-use/feedapi/"));
        expectedHandlerMappings.forEach((handler, bindings) -> validateHandler(handlers.get(handler), bindingPrefix, bindings));

        // All other handlers should be bound to default (http://*/...)
        handlers.entrySet().stream()
                .filter(e -> ! expectedHandlerMappings.containsKey(e.getKey()))
                .forEach(e -> assertTrue(e.getValue().serverBindings().stream().allMatch(s -> s.startsWith("http://*/"))));

    }

    private void validateHandler(JdiscBindingsConfig.Handlers handler, String bindingPrefix, List<String> expectedBindings) {
        assertNotNull(handler);
        assertEquals(expectedBindings.size(), handler.serverBindings().size());
        assertThat(handler.serverBindings(), containsInAnyOrder(expectedBindings.stream().map(s->bindingPrefix+s).toArray()));
    }

    private void createClusterWithJDiscHandler() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <handler id='discHandler'>",
                "    <binding>http://*/binding0</binding>",
                "    <binding>http://*/binding1</binding>",
                "  </handler>",
                "</container>");

        createModel(root, clusterElem);
    }

}
