// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the agent skills enabled in &lt;skills&gt;.
 *
 * @author ingrid
 */
public class SkillsBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    void skill_handler_is_added_when_enabled() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search />",
                "  <skills>",
                "    <schemas/>",
                "  </skills>",
                nodesXml,
                "</container>");

        createModel(root, clusterElem);

        var discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");
        assertEquals("http://*/skills/v1", discBindingsConfig.handlers(SkillHandler.HANDLER_CLASSNAME).serverBindings(0));
        assertEquals("http://*/skills/v1/*", discBindingsConfig.handlers(SkillHandler.HANDLER_CLASSNAME).serverBindings(1));
        assertTrue(hasSkillComponent());
    }

    @Test
    void skill_is_not_added_unless_enabled() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search />",
                nodesXml,
                "</container>");

        createModel(root, clusterElem);

        var discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");
        assertFalse(discBindingsConfig.handlers().containsKey(SkillHandler.HANDLER_CLASSNAME));
        assertFalse(hasSkillComponent());
    }

    @Test
    void skill_handler_is_bound_where_search_is() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search>",
                "    <binding>http://*:8081/search/*</binding>",
                "    <binding>http://localhost:8082/search/*</binding>",
                "    <binding>http://*:8081/other-search/*</binding>",
                "  </search>",
                "  <skills>",
                "    <schemas/>",
                "  </skills>",
                nodesXml,
                "</container>");

        createModel(root, clusterElem);

        var discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");
        assertEquals(List.of("http://*:8081/skills/v1", "http://*:8081/skills/v1/*",
                             "http://localhost:8082/skills/v1", "http://localhost:8082/skills/v1/*"),
                     discBindingsConfig.handlers(SkillHandler.HANDLER_CLASSNAME).serverBindings());
    }

    @Test
    void skill_is_not_added_but_warned_about_when_search_has_no_bindings() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <search>",
                "    <binding/>",
                "  </search>",
                "  <skills>",
                "    <schemas/>",
                "  </skills>",
                nodesXml,
                "</container>");

        var logger = new TestLogger();
        createModel(root, logger, clusterElem);

        var discBindingsConfig = root.getConfig(JdiscBindingsConfig.class, "default");
        assertFalse(discBindingsConfig.handlers().containsKey(SkillHandler.HANDLER_CLASSNAME));
        assertFalse(hasSkillComponent());
        assertTrue(logger.msgs.stream().anyMatch(msg -> msg.getFirst() == Level.WARNING &&
                                                        msg.getSecond().contains("'schemas' skill") &&
                                                        msg.getSecond().contains("'default'")),
                   logger.msgs.toString());
    }

    @Test
    void schemas_skill_requires_search() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'>",
                "  <skills>",
                "    <schemas/>",
                "  </skills>",
                nodesXml,
                "</container>");

        var e = assertThrows(IllegalArgumentException.class, () -> createModel(root, clusterElem));
        assertEquals("The 'schemas' skill requires search, but container cluster 'default' has no <search>", e.getMessage());
    }

    private boolean hasSkillComponent() {
        return root.getConfig(ComponentsConfig.class, "default").components().stream()
                   .anyMatch(component -> component.id().equals(SkillHandler.SCHEMA_SKILL_CLASSNAME));
    }

}
