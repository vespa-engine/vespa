package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.usability.BindingsOverviewHandler;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import org.junit.Test;
import org.w3c.dom.Element;

import static com.yahoo.vespa.model.container.ContainerCluster.ROOT_HANDLER_BINDING;
import static com.yahoo.vespa.model.container.ContainerCluster.STATE_HANDLER_BINDING_1;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for container model building with custom handlers.
 *
 * @author gjoranv
 */
public class HandlerBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    public void handlers_are_included_in_components_config() {
        createClusterWithJDiscHandler();
        assertThat(componentsConfig().toString(), containsString(".id \"discHandler\""));
    }

    @Test
    public void handler_bindings_are_included_in_discBindings_config() {
        createClusterWithJDiscHandler();
        String discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default").toString();
        assertThat(discBindingsConfig, containsString(".serverBindings[0] \"http://*/binding0\""));
        assertThat(discBindingsConfig, containsString(".serverBindings[1] \"http://*/binding1\""));
    }

    @Test
    public void nested_components_are_injected_to_handlers() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <handler id='myHandler'>",
                "    <component id='injected' />",
                "  </handler>",
                "</container>");

        createModel(root, clusterElem);
        Component<?,?> handler = getComponent("default", "myHandler");
        assertThat(handler.getInjectedComponentIds(), hasItem("injected@myHandler"));
    }

    @Test
    public void default_root_handler_binding_can_be_stolen_by_user_configured_handler() {
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
    public void reserved_binding_cannot_be_stolen_by_user_configured_handler() {
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
    public void custom_handler_gets_default_threadpool() {
        createClusterWithJDiscHandler();
        ApplicationContainerCluster cluster = (ApplicationContainerCluster)root.getChildren().get("default");
        Handler handler = cluster.getHandlers().stream()
                .filter(h -> h.getComponentId().toString().equals("discHandler"))
                .findAny().orElseThrow();

        assertTrue(handler.getInjectedComponentIds().contains("threadpool@default-handler-common"));
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
