// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.StringReader;

/**
 * Demonstrates that only the most specific match is retained and that this can be overridden by using ids.
 *
 * @author bratseth
 */
public class MultiOverrideProcessorTest {

    private static final String input =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <services version="1.0" xmlns:deploy="vespa">
                <container id='default' version='1.0'>
                    <component id="comp-B" class="com.yahoo.ls.MyComponent" bundle="lsbe-hv">
                        <config name="ls.config.resource-pool">
                            <resource>
                                <item>
                                    <id>comp-B-item-0</id>
                                    <type></type>
                                </item>
                                <item deploy:environment="dev perf test staging prod" deploy:region="us-west-1 us-east-3">
                                    <id>comp-B-item-1</id>
                                    <type></type>
                                </item>
                                <item>
                                    <id>comp-B-item-2</id>
                                    <type></type>
                                </item>
                            </resource>
                        </config>
                    </component>
                </container>
            </services>
            """;

    private static final String inputWithIds =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <services version="1.0" xmlns:deploy="vespa">
                <container id='default' version='1.0'>
                    <component id="comp-B" class="com.yahoo.ls.MyComponent" bundle="lsbe-hv">
                        <config name="ls.config.resource-pool">
                            <resource>
                                <item id='1'>
                                    <id>comp-B-item-0</id>
                                    <type></type>
                                </item>
                                <item  id='2' deploy:environment="dev perf test staging prod" deploy:region="us-west-1 us-east-3">
                                    <id>comp-B-item-1</id>
                                    <type></type>
                                </item>
                                <item id='3'>
                                    <id>comp-B-item-2</id>
                                    <type></type>
                                </item>
                            </resource>
                        </config>
                    </component>
                </container>
            </services>
            """;

    @Test
    public void testParsingDev() throws TransformerException {
        String expected =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <services version="1.0" xmlns:deploy="vespa">
                    <container id='default' version='1.0'>
                        <component id="comp-B" class="com.yahoo.ls.MyComponent" bundle="lsbe-hv">
                            <config name="ls.config.resource-pool">
                                <resource>
                                    <item>
                                        <id>comp-B-item-1</id>
                                        <type></type>
                                    </item>
                                </resource>
                            </config>
                        </component>
                    </container>
                </services>""";
        assertOverride(Environment.dev, RegionName.from("us-east-3"), expected);
    }

    @Test
    public void testParsingDevWithIds() throws TransformerException {
        String expected =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <services version="1.0" xmlns:deploy="vespa">
                    <container id='default' version='1.0'>
                        <component id="comp-B" class="com.yahoo.ls.MyComponent" bundle="lsbe-hv">
                            <config name="ls.config.resource-pool">
                                <resource>
                                    <item id='1'>
                                        <id>comp-B-item-0</id>
                                        <type></type>
                                    </item>
                                    <item id='2'>
                                        <id>comp-B-item-1</id>
                                        <type></type>
                                    </item>
                                    <item id='3'>
                                        <id>comp-B-item-2</id>
                                        <type></type>
                                    </item>
                                </resource>
                            </config>
                        </component>
                    </container>
                </services>""";
        assertOverrideWithIds(Environment.dev, RegionName.from("us-east-3"), expected);
    }

    private void assertOverride(Environment environment, RegionName region, String expected) throws TransformerException {
        Document inputDoc = Xml.getDocument(new StringReader(input));
        Document newDoc = new OverrideProcessor(InstanceName.from("default"), environment, region, Tags.empty()).process(inputDoc);
        TestBase.assertDocument(expected, newDoc);
    }

    private void assertOverrideWithIds(Environment environment, RegionName region, String expected) throws TransformerException {
        Document inputDoc = Xml.getDocument(new StringReader(inputWithIds));
        Document newDoc = new OverrideProcessor(InstanceName.from("default"), environment, region, Tags.empty()).process(inputDoc);
        TestBase.assertDocument(expected, newDoc);
    }

}
