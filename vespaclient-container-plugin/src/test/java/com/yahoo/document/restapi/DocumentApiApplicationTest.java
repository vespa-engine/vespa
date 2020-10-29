// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import org.junit.Test;

/**
 * @author bratseth
 */
public class DocumentApiApplicationTest {

    /** Test that it is possible to instantiate an Application with a document-api */
    @Test
    public void application_with_document_api() {
        String services =
                "<container version='1.0'>" +
                "    <http><server port=\"0\" id=\"foobar\"/></http>" +
                "    <document-api/>" +
                "</container>";
        try (Application application = Application.fromServicesXml(services, Networking.enable)) {
        }
    }

}
