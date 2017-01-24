// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.container.logging.AccessLog;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.restapi.OperationHandler;

import java.util.concurrent.Executor;

/**
 * For setting up RestApi with a simple document type manager.
 *
 * @author dybis
 */
public class RestApiWithTestDocumentHandler extends RestApi{

    private DocumentTypeManager docTypeManager = new DocumentTypeManager();

    public RestApiWithTestDocumentHandler(
            Executor executor,
            AccessLog accessLog,
            OperationHandler operationHandler) {
        super(executor, accessLog, operationHandler, 20);

        DocumentType documentType = new DocumentType("testdocument");

        documentType.addField("title", DataType.STRING);
        documentType.addField("body", DataType.STRING);
        docTypeManager.registerDocumentType(documentType);

        setDocTypeManagerForTests(docTypeManager);
    }

}
