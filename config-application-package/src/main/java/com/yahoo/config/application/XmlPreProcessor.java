// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.application.FileSystemWrapper.FileWrapper;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
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
 */
public class XmlPreProcessor {

    final static String deployNamespace = "xmlns:deploy";
    final static String deployNamespaceUri = "vespa";
    final static String preprocessNamespace = "xmlns:preprocess";
    final static String preprocessNamespaceUri = "properties";  //TODO

    private final FileWrapper applicationDir;
    private final Reader xmlInput;
    private final InstanceName instance;
    private final Environment environment;
    private final RegionName region;
    private final List<PreProcessor> chain;

    public XmlPreProcessor(File applicationDir, File xmlInput, InstanceName instance, Environment environment, RegionName region) throws IOException {
        this(applicationDir, new FileReader(xmlInput), instance, environment, region);
    }

    public XmlPreProcessor(File applicationDir, Reader xmlInput, InstanceName instance, Environment environment, RegionName region) {
        this(FileSystemWrapper.getDefault().wrap(applicationDir.toPath()), xmlInput, instance, environment, region);
    }

    public XmlPreProcessor(FileWrapper applicationDir, Reader xmlInput, InstanceName instance, Environment environment, RegionName region) {
        this.applicationDir = applicationDir;
        this.xmlInput = xmlInput;
        this.instance = instance;
        this.environment = environment;
        this.region = region;
        this.chain = setupChain();
    }

    public Document run() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilder docBuilder = Xml.getPreprocessDocumentBuilder();
        Document document = docBuilder.parse(new InputSource(xmlInput));
        return execute(document);
    }

    private Document execute(Document input) throws IOException, TransformerException {
        for (PreProcessor preProcessor : chain) {
            input = preProcessor.process(input);
        }
        return input;
    }

    private List<PreProcessor> setupChain() {
        List<PreProcessor> chain = new ArrayList<>();
        chain.add(new IncludeProcessor(applicationDir));
        chain.add(new OverrideProcessor(instance, environment, region));
        chain.add(new PropertiesProcessor());
        return chain;
    }

}
