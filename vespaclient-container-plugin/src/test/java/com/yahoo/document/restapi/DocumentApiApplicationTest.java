// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * @author bratseth
 */
public class DocumentApiApplicationTest {

    /** Test that it is possible to instantiate an Application with a document-api */
    @Test
    public void application_with_document_api() throws IOException {
        String services =
                "<jdisc version='1.0'>" +
                "    <http><server port=\"" + findRandomOpenPortOnAllLocalInterfaces() + "\" id=\"foobar\"/></http>" +
                "    <document-api/>" +
                "</jdisc>";
        try (Application application = Application.fromServicesXml(services, Networking.enable)) {
        }
    }

    private int findRandomOpenPortOnAllLocalInterfaces() throws IOException {
        ServerSocket socket = new ServerSocket(0);
        socket.setReuseAddress(true);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

}
