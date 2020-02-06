// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.gui;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Henrik Høiness
 */

public class GUIHandlerTest {

    private JDisc container;

    @Before
    public void startContainer() {
        container = JDisc.fromServicesXml(servicesXml(), Networking.disable);
    }

    @After
    public void stopContainer() {
        /*
        try {
            Thread.sleep(100_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        container.close();
    }

    @Test
    public void testRequest() throws Exception {
       assertResponse("/querybuilder/", "<!-- Copyright 2018 Yahoo Holdings.","text/html; charset=UTF-8", 200);
    }

    @Test
    public void testContentTypes() throws Exception{
        assertResponse("/querybuilder/_includes/css/vespa.css", "/**","text/css; charset=UTF-8", 200);
        assertResponse("/querybuilder/js/agency.js", "/*!","application/javascript; charset=UTF-8", 200);
        assertResponse("/querybuilder/img/reload.svg", "<?xml","image/svg+xml; charset=UTF-8", 200);
        assertResponse("/querybuilder/img/Vespa-V2.png", null,"image/png; charset=UTF-8", 200);
    }

    @Test
    public void testInvalidPath() throws Exception{
        assertResponse("/querybuilder/invalid_filepath", "{\"error-code\":\"NOT_FOUND\",\"message\":\"Nothing at path","application/json; charset=UTF-8", 404);
    }


    private void assertResponse(String path, String expectedStartString,  String expectedContentType, int expectedStatusCode) throws IOException {
        assertResponse(Request.Method.GET, path, expectedStartString,expectedContentType, expectedStatusCode);
    }

    private void assertResponse(Request.Method method, String path, String expectedStartString, String expectedContentType, int expectedStatusCode) throws IOException {
        Response response = container.handleRequest(new Request("http://localhost:8080" + path, new byte[0], method));
        assertEquals("Status code", expectedStatusCode, response.getStatus());
        assertEquals(expectedContentType, response.getHeaders().getFirst("Content-Type"));
        if(expectedStartString != null){
            assertTrue(response.getBodyAsString().startsWith(expectedStartString));
        }
    }

    private String servicesXml() {
        return "<container version='1.0'>\n" +
                "  <accesslog type='disabled'/>\n" +
                "  <handler id='com.yahoo.search.query.gui.GUIHandler'>\n" +
                "    <binding>http://*/querybuilder/*</binding>\n" +
                "  </handler>\n" +
                "  <http>\n" +
                "    <server id='default' port='8080'/>\n" +
                "  </http>\n" +
                "</container>";
    }

}