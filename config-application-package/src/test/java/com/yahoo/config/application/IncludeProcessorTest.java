// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.application.api.ApplicationPackage;
import org.junit.Assert;
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
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
                "    <preprocess:properties>\n" +
                "        <qrs.port>4099</qrs.port>\n" +
                "        <qrs.port>5000</qrs.port>\n" +
                "    </preprocess:properties>\n" +
                "    <preprocess:properties deploy:environment='prod'>\n" +
                "        <qrs.port deploy:region='us-west'>5001</qrs.port>" +
                "        <qrs.port deploy:region='us-east us-central'>5002</qrs.port>" +
                "    </preprocess:properties>\n" +
                "    <admin version=\"2.0\">\n" +
                "        <adminserver hostalias=\"node0\"/>\n" +
                "    </admin>\n" +
                "    <admin deploy:environment=\"staging prod\" deploy:region=\"us-east us-central\" version=\"2.0\">\n" +
                "        <adminserver hostalias=\"node1\"/>\n" +
                "    </admin>\n" +
                "    <content id=\"foo\" version=\"1.0\">\n" +
                "    <redundancy>1</redundancy><documents>\n" +
                "    <document mode=\"index\" type=\"music.sd\"/>\n" +
                "    </documents><nodes>\n" +
                "    <node distribution-key=\"0\" hostalias=\"node0\"/>\n" +
                "    </nodes>" +
                "    <nodes deploy:environment=\"prod\">\n" +
                "    <node distribution-key=\"0\" hostalias=\"node0\"/>\n" +
                "    <node distribution-key=\"1\" hostalias=\"node1\"/>\n" +
                "    </nodes>" +
                "    <nodes deploy:environment=\"prod\" deploy:region=\"us-west\">\n" +
                "    <node distribution-key=\"0\" hostalias=\"node0\"/>\n" +
                "    <node distribution-key=\"1\" hostalias=\"node1\"/>\n" +
                "    <node distribution-key=\"2\" hostalias=\"node2\"/>\n" +
                "    </nodes>" +
                "</content>\n" +
                "<container id=\"stateless\" version=\"1.0\">\n" +
                "    <search deploy:environment=\"prod\">\n" +
                "      <chain id=\"common\">\n" +
                "        <searcher id=\"MySearcher1\" />\n" +
                "        <searcher deploy:environment=\"prod\" id=\"MySearcher2\" />\n" +
                "      </chain>\n" +
                "    </search>\n" +
                "    <search/>\n" +
                "    <component id=\"foo\" class=\"MyFoo\" bundle=\"foobundle\" />\n" +
                "    <component id=\"bar\" class=\"TestBar\" bundle=\"foobundle\" deploy:environment=\"dev\" />\n" +
                "    <component id=\"bar\" class=\"ProdBar\" bundle=\"foobundle\" deploy:environment=\"prod\" />\n" +
                "    <component id=\"baz\" class=\"ProdBaz\" bundle=\"foobundle\" deploy:environment=\"prod\" />\n" +
                "    <nodes>\n" +
                "        <node baseport=\"${qrs.port}\" hostalias=\"node0\"/>\n" +
                "    </nodes>\n" +
                "</container></services>";

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
