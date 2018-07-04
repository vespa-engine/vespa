package com.yahoo.vespa.hosted.controller.restapi.gui;

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

public class GUIHandlerTest {

    private JDisc container;

    @Before
    public void startContainer() {
        container = JDisc.fromServicesXml(servicesXml(), Networking.enable);
    }

    @After
    public void stopContainer() {
    /*
        try {
            Thread.sleep(120_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        container.close();
    }

    @Test
    public void testRequest() throws Exception {
       assertResponse("/querybuilder/", "<!DOCTYPE html>","text/html; charset=UTF-8", 200);
    }

    @Test
    public void testContentTypes() throws Exception{
        assertResponse("/querybuilder/_includes/css/vespa.css", ":root","text/css; charset=UTF-8", 200);
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
        return "<jdisc version='1.0'>\n" +
                "  <handler id='com.yahoo.vespa.hosted.controller.restapi.gui.GUIHandler'>\n" +
                "    <binding>http://*/querybuilder/*</binding>\n" +
                "  </handler>\n" +
                "  <http>\n" +
                "    <server id='default' port='8080'/>\n" +
                "  </http>\n" +
                "</jdisc>";
    }

}