// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tls;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.component.ComponentId;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyStoreUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author mpolden
 */
public class SecureContainerTest {

    private JDisc container;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void startContainer() {
        container = JDisc.fromServicesXml(servicesXml(writeKeyStore()), Networking.enable);
    }

    @After
    public void stopContainer() {
        container.close();
    }

    @Test
    public void test_https_request() {
        assertNotNull("SslContextFactoryProvider is created", sslContextFactoryProvider());
        assertResponse(Request.Method.GET, "/", 200);
    }

    private void assertResponse(Request.Method method, String path, int expectedStatusCode) {
        Response response = container.handleRequest(new Request("https://localhost:9999" + path, new byte[0], method));
        assertEquals("Status code", expectedStatusCode, response.getStatus());
    }

    private ControllerSslContextFactoryProvider sslContextFactoryProvider() {
        return (ControllerSslContextFactoryProvider) container.components().getComponent(ComponentId.fromString("ssl-provider@default"));
    }

    private String servicesXml(Path trustStore) {
        return "<container version='1.0'>\n" +
               "  <config name=\"container.handler.threadpool\">\n" +
               "    <maxthreads>10</maxthreads>\n" +
               "  </config> \n" +
               "  <config name='vespa.hosted.controller.tls.config.tls'>\n" +
               "    <caTrustStore>" + trustStore.toString() + "</caTrustStore>\n" +
               "    <certificateSecret>controller.cert</certificateSecret>\n" +
               "    <privateKeySecret>controller.key</privateKeySecret>\n" +
               "  </config>\n" +
               "  <component id='com.yahoo.vespa.hosted.controller.tls.SecretStoreMock'/>\n" +
               "  <http>\n" +
               "    <server id='default' port='9999'>\n" +
               "      <ssl-provider class='com.yahoo.vespa.hosted.controller.tls.ControllerSslContextFactoryProvider' bundle='controller-server'/>\n" +
               "    </server>\n" +
               "  </http>\n" +
               "</container>";
    }

    private Path writeKeyStore()  {
        KeyStore keyStore = KeyStoreBuilder.withType(KeyStoreType.JKS)
                                           .withKeyEntry(getClass().getSimpleName(),
                                                         Keys.keyPair.getPrivate(), new char[0], Keys.certificate)
                                           .build();
        try {
            Path path = folder.newFile().toPath();
            KeyStoreUtils.writeKeyStoreToFile(keyStore, path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
