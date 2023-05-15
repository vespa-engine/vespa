package com.yahoo.config.application;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.transform.TransformerException;
import java.io.IOException;

public class ValidationProcessor implements PreProcessor {

    @Override
    public Document process(Document input) throws IOException, TransformerException {
        NodeList includeitems = input.getElementsByTagNameNS("http://www.w3.org/2001/XInclude", "*");
        if (includeitems.getLength() > 0)
            throw new UnsupportedOperationException("XInclude not supported, use preprocess:include instead");
        return input;
    }

}