// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder;

import com.yahoo.test.ArraytypesConfig;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.ConfigDefinitionStore;
import com.yahoo.test.SimpletypesConfig;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class UserConfigBuilderTest {

    private final ConfigDefinitionStore configDefinitionStore = defKey -> Optional.empty();

    @Test
    public void require_that_simple_config_is_resolved() {
        Element configRoot = getDocument("<config name=\"simpletypes\">" +
                                         "    <intval>13</intval>" +
                                         "</config>" +
                                         "<config name=\"simpletypes\" version=\"1\">" +
                                         "    <stringval>foolio</stringval>" +
                                         "</config>");
        UserConfigRepo map = UserConfigBuilder.build(configRoot, configDefinitionStore, new BaseDeployLogger());
        assertFalse(map.isEmpty());
        ConfigDefinitionKey key = new ConfigDefinitionKey("simpletypes", "config");
        assertNotNull(map.get(key));
        SimpletypesConfig config = createConfig(SimpletypesConfig.class, map.get(key));
        assertThat(config.intval(), is(13));
        assertThat(config.stringval(), is("foolio"));
    }

    @Test
    public void require_that_arrays_config_is_resolved() {
        Element configRoot = getDocument("<config name=\"arraytypes\">" +
                                                 "    <intarr operation=\"append\">13</intarr>" +
                                                 "    <intarr operation=\"append\">10</intarr>" +
                                                 "    <intarr operation=\"append\">1337</intarr>" +
                                                 "</config>");
        UserConfigRepo map = UserConfigBuilder.build(configRoot, configDefinitionStore, new BaseDeployLogger());
        assertFalse(map.isEmpty());
        ConfigDefinitionKey key = new ConfigDefinitionKey("arraytypes", "config");
        assertNotNull(map.get(key));
        ArraytypesConfig config = createConfig(ArraytypesConfig.class, map.get(key));
        assertEquals(Arrays.asList(13,10,1337), config.intarr());

        Element configRoot2 = getDocument("<config name=\"arraytypes\">" +
                                                  "    <intarr operation=\"clear\">456</intarr>" +
                                                  "</config>");
        UserConfigRepo map2 = UserConfigBuilder.build(configRoot2, configDefinitionStore, new BaseDeployLogger());
        map.merge(map2);
        ArraytypesConfig config2 = createConfig(ArraytypesConfig.class, map2.get(key));
        assertEquals(Collections.singletonList(456), config2.intarr());
    }

    @Test
    public void require_that_array_with_items_is_resolved() {
        ConfigDefinitionKey key = new ConfigDefinitionKey("arraytypes", "config");

        Element configRoot = getDocument("<config name='arraytypes'>" +
                                                 "    <intarr operation='clear'>" +
                                                 "      <item>0</item>" +
                                                 "    </intarr>" +
                                                 "</config>");
        UserConfigRepo map = UserConfigBuilder.build(configRoot, configDefinitionStore, new BaseDeployLogger());
        assertFalse(map.isEmpty());
        assertNotNull(map.get(key));
        ArraytypesConfig config = createConfig(ArraytypesConfig.class, map.get(key));
        assertEquals(Collections.singletonList(0), config.intarr());

        Element configRoot2 = getDocument("<config name='arraytypes'>" +
                                                 "    <intarr>" +
                                                 "      <item>1</item>" +
                                                 "      <item>2</item>" +
                                                 "    </intarr>" +
                                                 "</config>");
        UserConfigRepo map2 = mergeAndCreateConfig(map, configRoot2);
        ArraytypesConfig config2 = createConfig(ArraytypesConfig.class, map2.get(key));
        assertEquals(Arrays.asList(1, 2), config2.intarr());


        Element configRoot3 = getDocument("<config name='arraytypes'>" +
                                                  "    <intarr operation='clear'>" +
                                                  "      <item>3</item>" +
                                                  "    </intarr>" +
                                                  "</config>");
        UserConfigRepo map3 = mergeAndCreateConfig(map2, configRoot3);
        ArraytypesConfig config3 = createConfig(ArraytypesConfig.class, map3.get(key));
        assertEquals(Collections.singletonList(3), config3.intarr());
    }

    @Test
    public void require_that_arrays_of_structs_are_resolved() {
        Element configRoot = getDocument(
                "  <config name='vespa.configdefinition.specialtokens'>" +
                        "    <tokenlist operation='append'>" +
                        "      <name>default</name>" +
                        "      <tokens operation='append'>" +
                        "        <token>dvd+-r</token>" +
                        "      </tokens>" +
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
        assertThat(config.tokenlist().size(), is(1));
        assertThat(config.tokenlist().get(0).name(), is("default"));
        assertThat(config.tokenlist().get(0).tokens().size(), is(1));
        assertThat(config.tokenlist().get(0).tokens().get(0).token(), is("dvd+-r"));
    }

    @Test
    public void no_exception_when_config_class_does_not_exist() {
        Element configRoot = getDocument("<config name=\"unknown\">" +
                "    <foo>1</foo>" +
                "</config>");
        UserConfigRepo repo = UserConfigBuilder.build(configRoot, configDefinitionStore, new BaseDeployLogger());
        ConfigPayloadBuilder builder = repo.get(new ConfigDefinitionKey("unknown", "config"));
        assertNotNull(builder);
    }

    private Element getDocument(String xml) {
        Reader xmlReader = new StringReader("<model>" + xml + "</model>");
        Document doc;
        try {
            doc = XmlHelper.getDocumentBuilder().parse(new InputSource(xmlReader));
        } catch (Exception e) {
            throw new RuntimeException();
        }
        return doc.getDocumentElement();
    }

    private static <ConfigType extends ConfigInstance> ConfigType createConfig(Class<ConfigType> clazz, ConfigPayloadBuilder builder) {
        return ConfigPayload.fromBuilder(builder).toInstance(clazz, "");
    }

    private UserConfigRepo mergeAndCreateConfig(UserConfigRepo original, Element newElement) {
        UserConfigRepo map = UserConfigBuilder.build(newElement, configDefinitionStore, new BaseDeployLogger());
        original.merge(map);
        return map;
    }

}
