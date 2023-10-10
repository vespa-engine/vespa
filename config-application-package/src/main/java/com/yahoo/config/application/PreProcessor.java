// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.IOException;

/**
 * Performs pre-processing of XML document and returns new document that has been processed.
 *
 * @author Ulf Lilleengen
 */
public interface PreProcessor {

    Document process(Document input) throws IOException, TransformerException;

}
