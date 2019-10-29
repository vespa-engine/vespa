// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.hosted.ca.restapi.mock.SecretStoreMock;
import org.junit.After;
import org.junit.Before;

import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

/**
 * The superclass of REST API tests which require a functional container instance.
 *
 * @author mpolden
 */
public class ContainerTester {

    private JDisc container;

    @Before
    public void startContainer() {
        container = JDisc.fromServicesXml(servicesXml(), Networking.enable);
    }

    @After
    public void stopContainer() {
        container.close();
    }

    public SecretStoreMock secretStore() {
        return (SecretStoreMock) container.components().getComponent(SecretStoreMock.class.getName());
    }

    public void assertResponse(int expectedStatus, String expectedBody, Request request) {
        assertResponse(expectedStatus, (body) -> assertEquals(expectedBody, body), request);
    }

    public void assertResponse(int expectedStatus, Consumer<String> bodyAsserter, Request request) {
        var response = container.handleRequest(request);
        try {
            bodyAsserter.accept(response.getBodyAsString());
        } catch (CharacterCodingException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(expectedStatus, response.getStatus());
        assertEquals("application/json; charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
    }

    private static String servicesXml() {
        return "<container version='1.0'>\n" +
               "  <config name=\"container.handler.threadpool\">\n" +
               "    <maxthreads>10</maxthreads>\n" +
               "  </config>\n" +
               "  <config name='vespa.hosted.athenz.instanceproviderservice.config.athenz-provider-service'>\n" +
               "    <athenzCaTrustStore>/path/to/file</athenzCaTrustStore>\n" +
               "    <domain>vespa.external</domain>\n" +
               "    <serviceName>servicename</serviceName>\n" +
               "    <secretName>secretname</secretName>\n" +
               "    <secretVersion>0</secretVersion>\n" +
               "    <certDnsSuffix>suffix</certDnsSuffix>\n" +
               "    <ztsUrl>https://localhost:123/</ztsUrl>\n" +
               "  </config>\n" +
               "  <component id='com.yahoo.vespa.hosted.ca.restapi.mock.SecretStoreMock'/>\n" +
               "  <component id='com.yahoo.vespa.hosted.ca.restapi.mock.InstanceValidatorMock'/>\n" +
               "  <handler id='com.yahoo.vespa.hosted.ca.restapi.CertificateAuthorityApiHandler'>\n" +
               "    <binding>http://*/ca/v1/*</binding>\n" +
               "  </handler>\n" +
               "  <http>\n" +
               "    <server id='default' port='12345'/>\n" +
               "    <filtering>\n" +
               "      <request-chain id=\"my-default-chain\">\n" +
               "        <filter id='com.yahoo.vespa.hosted.ca.restapi.mock.PrincipalFromHeaderFilter' />\n" +
               "        <binding>http://*/*</binding>\n" +
               "      </request-chain>\n" +
               "    </filtering>\n" +
               "  </http>\n" +
               "</container>";
    }

}