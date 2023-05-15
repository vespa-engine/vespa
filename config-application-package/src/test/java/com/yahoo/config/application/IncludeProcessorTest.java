// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.application.api.ApplicationPackage;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class IncludeProcessorTest {

    @Test
    public void testInclude() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        File app = new File("src/test/resources/multienvapp");
        DocumentBuilder docBuilder = Xml.getPreprocessDocumentBuilder();

        String expected =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. --><services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <preprocess:properties>
                        <qrs.port>4099</qrs.port>
                        <qrs.port>5000</qrs.port>
                    </preprocess:properties>
                    <preprocess:properties deploy:environment="prod">
                        <qrs.port deploy:region="us-west">5001</qrs.port>
                        <qrs.port deploy:region="us-east us-central">5002</qrs.port>
                    </preprocess:properties>
                    <admin version="2.0">
                        <adminserver hostalias="node0"/>
                    </admin>
                    <admin deploy:environment="staging prod" deploy:region="us-east us-central" version="2.0">
                        <adminserver hostalias="node1"/>
                    </admin>
                    <content id="foo" version="1.0">
                        <thread count="128" deploy:region="us-central us-east"/>
                        <redundancy>1</redundancy>
                        <documents>
                            <document mode="index" type="music.sd"/>
                        </documents>
                        <nodes>
                            <node distribution-key="0" hostalias="node0"/>
                        </nodes>
                        <nodes deploy:environment="prod">
                            <node distribution-key="0" hostalias="node0"/>
                            <node distribution-key="1" hostalias="node1"/>
                        </nodes>
                        <nodes deploy:environment="prod" deploy:region="us-west">
                            <node distribution-key="0" hostalias="node0"/>
                            <node distribution-key="1" hostalias="node1"/>
                            <node distribution-key="2" hostalias="node2"/>
                        </nodes>
                    </content>
                    <container id="stateless" version="1.0">
                        <search deploy:environment="prod">
                            <chain id="common">
                                <searcher id="MySearcher1"/>
                                <searcher deploy:environment="prod" id="MySearcher2"/>
                            </chain>
                        </search>
                        <search/>
                        <component bundle="foobundle" class="MyFoo" id="foo"/>
                        <component bundle="foobundle" class="TestBar" deploy:environment="dev" id="bar"/>
                        <component bundle="foobundle" class="ProdBar" deploy:environment="prod" id="bar"/>
                        <component bundle="foobundle" class="ProdBaz" deploy:environment="prod" id="baz"/>
                        <nodes>
                            <node baseport="${qrs.port}" hostalias="node0"/>
                        </nodes>
                    </container>
                </services>""";

        Document doc = new IncludeProcessor(app).process(docBuilder.parse(getServices(app)));
        // System.out.println(Xml.documentAsString(doc));
        TestBase.assertDocument(expected, doc);
    }

    @Test
    public void testIllegalParent() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        try {
            File app = new File("src/test/resources/multienvapp_fail_parent");
            DocumentBuilder docBuilder = Xml.getPreprocessDocumentBuilder();
            new IncludeProcessor(app).process(docBuilder.parse(getServices(app)));
            fail("sibling to package should not be allowed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("src/test/resources/multienvapp_fail_parent/../multienvapp/services.xml is not a descendant of src/test/resources/multienvapp_fail_parent", e.getMessage());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalParent2() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        File app = new File("src/test/resources/multienvapp_fail_parent2");
        DocumentBuilder docBuilder = Xml.getPreprocessDocumentBuilder();
        new IncludeProcessor(app).process(docBuilder.parse(getServices(app)));
        fail("absolute include path should not be allowed");
    }

    @Test(expected = NoSuchFileException.class)
    public void testRequiredIncludeIsDefault() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        File app = new File("src/test/resources/multienvapp_failrequired");
        DocumentBuilder docBuilder = Xml.getPreprocessDocumentBuilder();
        new IncludeProcessor(app).process(docBuilder.parse(getServices(app)));
        fail("should fail by default to include a non-existent file");
    }

    static File getServices(File app) {
        return new File(app, ApplicationPackage.SERVICES);
    }

}
