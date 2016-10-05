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
                "    <document-api/>" +
                "</container>";
        try (Application application = Application.fromServicesXml(services, Networking.enable)) {
        }
    }

}
