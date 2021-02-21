// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.docproc;

import com.yahoo.collections.Pair;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.chains.ChainedComponentModelBuilder;
import com.yahoo.vespa.model.container.docproc.model.DocumentProcessorModel;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Einar M R Rosenvinge
 */
public class DocumentProcessorModelBuilder extends ChainedComponentModelBuilder {

    private Map<Pair<String, String>, String> fieldNameSchemaMap = new HashMap<>();

    public DocumentProcessorModelBuilder(Element spec) {
        super(spec);
        readFieldNameSchemaMap(spec);
    }

    private void readFieldNameSchemaMap(Element spec) {
        fieldNameSchemaMap = parseFieldNameSchemaMap(spec);
    }

    @Override
    public DocumentProcessorModel build() {
        return new DocumentProcessorModel(bundleInstantiationSpec, dependencies, fieldNameSchemaMap);
    }

    /**
     * Parses a schemamapping element and generates a map of field mappings
     *
     * @param e a schemamapping element
     * @return doctype, in-document → in-processor
     */
    public static Map<Pair<String,String>, String> parseFieldNameSchemaMap(Element e) {
        Map<Pair<String, String>, String> ret = new HashMap<>();
        for (Element sm : XML.getChildren(e, "map")) {
            for (Element fm : XML.getChildren(sm, "field")) {
                String from = fm.getAttribute("in-document");
                String to = fm.getAttribute("in-processor");
                String doctype = fm.getAttribute("doctype");
                if ("".equals(doctype)) doctype=null;
                ret.put(new Pair<>(doctype, from), to);
            }
        }
        return ret;
    }

}
