// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A preprocessor for services.xml files that handles deploy:environment, deploy:region, preprocess:properties, preprocess:include
 * and create a new Document which is based on the supplied environment and region
 *
 * @author hmusum
 * @since 5.22
 */
public class XmlPreProcessor {

    final static String deployNamespace = "xmlns:deploy";
    final static String deployNamespaceUri = "vespa";
    final static String preprocessNamespace = "xmlns:preprocess";
    final static String preprocessNamespaceUri = "properties";  //TODO

    private final File applicationDir;
    private final Reader xmlInput;
    private final Environment environment;
    private final RegionName region;
    private final List<PreProcessor> chain;

    public XmlPreProcessor(File applicationDir, File xmlInput, Environment environment, RegionName region) throws IOException {
        this(applicationDir, new FileReader(xmlInput), environment, region);
    }

    public XmlPreProcessor(File applicationDir, Reader xmlInput, Environment environment, RegionName region) throws IOException {
        this.applicationDir = applicationDir;
        this.xmlInput = xmlInput;
        this.environment = environment;
        this.region = region;
        this.chain = setupChain();
    }

    public Document run() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilder docBuilder = Xml.getPreprocessDocumentBuilder();
        final Document document = docBuilder.parse(new InputSource(xmlInput));
        return execute(document);
    }

    private Document execute(Document input) throws IOException, TransformerException {
        for (PreProcessor preProcessor : chain) {
            input = preProcessor.process(input);
        }
        return input;
    }

    private List<PreProcessor> setupChain() throws IOException {
        List<PreProcessor> chain = new ArrayList<>();
        chain.add(new IncludeProcessor(applicationDir));
        chain.add(new OverrideProcessor(environment, region));
        chain.add(new PropertiesProcessor());
        return chain;
    }

}
