// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.ConfigDefinitionStore;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.test.SimpletypesConfig;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.Reader;
import java.io.StringReader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ulf Lilleengen
 */
public class UserConfigBuilderTest {

    private final ConfigDefinitionStore configDefinitionStore = defKey -> Optional.empty();

    @Test
    void require_that_simple_config_is_resolved() {
        Element configRoot = getDocument("<config name=\"test.simpletypes\">" +
                "    <intval>13</intval>" +
                "</config>" +
                "<config name=\"test.simpletypes\" version=\"1\">" +
                "    <stringval>foolio</stringval>" +
                "</config>");
        UserConfigRepo map = UserConfigBuilder.build(configRoot, configDefinitionStore, new BaseDeployLogger());
        assertFalse(map.isEmpty());
        ConfigDefinitionKey key = new ConfigDefinitionKey("simpletypes", "test");
        assertNotNull(map.get(key));
        SimpletypesConfig config = createConfig(SimpletypesConfig.class, map.get(key));
        assertEquals(13, config.intval());
        assertEquals("foolio", config.stringval());
    }

    private static <ConfigType extends ConfigInstance> ConfigType createConfig(Class<ConfigType> clazz, ConfigPayloadBuilder builder) {
        return ConfigPayload.fromBuilder(builder).toInstance(clazz, "");
    }

    @Test
    void require_that_arrays_of_structs_are_resolved() {
        Element configRoot = getDocument(
                "  <config name='vespa.configdefinition.specialtokens'>" +
                        "    <tokenlist>" +
                        "      <item>" +
                        "        <name>default</name>" +
                        "        <tokens>" +
                        "          <item>" +
                        "            <token>dvd+-r</token>" +
                        "          </item>" +
                        "          <item>" +
                        "            <token>c++</token>" +
                        "          </item>" +
                        "        </tokens>" +
                        "      </item>" +
                        "    </tokenlist>" +
                        "  </config>"
        );
        assertArraysOfStructs(configRoot);
    }

    private void assertArraysOfStructs(Element configRoot) {
        UserConfigRepo map = UserConfigBuilder.build(configRoot, configDefinitionStore, new BaseDeployLogger());
        assertFalse(map.isEmpty());
        ConfigDefinitionKey key = new ConfigDefinitionKey(SpecialtokensConfig.CONFIG_DEF_NAME, SpecialtokensConfig.CONFIG_DEF_NAMESPACE);
        assertNotNull(map.get(key));
        SpecialtokensConfig config = createConfig(SpecialtokensConfig.class, map.get(key));
        assertEquals(1, config.tokenlist().size());
        SpecialtokensConfig.Tokenlist tokenlist = config.tokenlist().get(0);
        assertEquals("default", tokenlist.name());
        assertEquals(2, tokenlist.tokens().size());
        assertEquals("dvd+-r", tokenlist.tokens().get(0).token());
    }

    @Test
    void no_exception_when_config_class_does_not_exist() {
        Element configRoot = getDocument("<config name=\"is.unknown\">" +
                "    <foo>1</foo>" +
                "</config>");
        UserConfigRepo repo = UserConfigBuilder.build(configRoot, configDefinitionStore, new BaseDeployLogger());
        ConfigPayloadBuilder builder = repo.get(new ConfigDefinitionKey("unknown", "is"));
        assertNotNull(builder);
    }

    private Element getDocument(String xml) {
        Reader xmlReader = new StringReader("<model>" + xml + "</model>");
        Document doc;
        try {
            doc = XmlHelper.getDocument(xmlReader);
        } catch (Exception e) {
            throw new RuntimeException();
        }
        return doc.getDocumentElement();
    }
}
