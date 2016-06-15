// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.IOException;

/**
 * Performs pre-processing of XML document and returns new document that has been processed.
 *
 * @author lulf
 * @since 5.21
 */
public interface PreProcessor {
    public Document process(Document input) throws IOException, TransformerException;
}
