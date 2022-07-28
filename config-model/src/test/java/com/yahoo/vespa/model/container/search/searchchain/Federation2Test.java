// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.test.SimpletypesConfig;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class Federation2Test extends SchemaChainsTestBase {
    @Override
    Element servicesXml() {
        return parse(
        "    <search>\n" +
        "\n" +
        "          <chain id=\"chain1\">\n" +
        "              <searcher id=\"com.yahoo.example.TestSearcher\">\n" +
        "                <config name=\"test.simpletypes\">\n" +
        "                  <stringval>testSearcher</stringval>\n" +
        "                </config>\n" +
        "              </searcher>\n" +
        "          </chain>\n" +
        "\n" +
        "          <provider id=\"test-source-inherits\">\n" +
        "              <searcher id=\"com.yahoo.example.AddHitSearcher\" />\n" +
        "              <source id=\"test-inherits\" />\n" +
        "          </provider>\n" +
        "\n" +
        "          <!-- Two providers with a common source -->\n" +
        "          <provider id=\"providerA\">\n" +
        "            <source id=\"commonSource\">\n" +
        "              <searcher id=\"com.yahoo.example.AddHitSearcher\">\n" +
        "                <config name=\"test.simpletypes\">\n" +
        "                  <stringval>providerA</stringval>\n" +
        "                </config>\n" +
        "              </searcher>\n" +
        "            </source>\n" +
        "          </provider>\n" +
        "\n" +
        "          <provider id=\"providerB\">\n" +
        "            <source idref=\"commonSource\">\n" +
        "              <searcher id=\"com.yahoo.example.AddHitSearcher\">\n" +
        "                <config name=\"test.simpletypes\">\n" +
        "                  <stringval>providerB</stringval>\n" +
        "                </config>\n" +
        "              </searcher>\n" +
        "            </source>\n" +
        "          </provider>\n" +
        "\n" +
        "      </search>\n");
    }


    @Test
    void testProviderConfigs() {
        //SimpletypesConfig testConfig = root.getConfig(SimpletypesConfig.class, "test/searchchains/chain/chain1/component/com.yahoo.example.TestSearcher");
        //assertEquals("testSearcher",testConfig.stringval());
        
        SimpletypesConfig configA = root.getConfig(SimpletypesConfig.class, "searchchains/chain/providerA/source/commonSource/component/com.yahoo.example.AddHitSearcher");
        assertEquals("providerA", configA.stringval());

        SimpletypesConfig configB = root.getConfig(SimpletypesConfig.class, "searchchains/chain/providerB/source/commonSource/component/com.yahoo.example.AddHitSearcher");
        assertEquals("providerB", configB.stringval());
    }

}
