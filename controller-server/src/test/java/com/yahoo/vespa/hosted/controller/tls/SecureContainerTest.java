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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author mpolden
 */
public class SecureContainerTest {

    private JDisc container;

    @TempDir
    public File folder;

    @BeforeEach
    public void startContainer() {
        container = JDisc.fromServicesXml(servicesXml(writeKeyStore()), Networking.enable);
    }

    @AfterEach
    public void stopContainer() {
        container.close();
    }

    @Test
    void test_https_request() {
        assertNotNull(sslContextFactoryProvider(), "SslContextFactoryProvider is created");
        assertResponse(Request.Method.GET, "/", 200);
    }

    private void assertResponse(Request.Method method, String path, int expectedStatusCode) {
        Response response = container.handleRequest(new Request("https://localhost:9999" + path, new byte[0], method));
        assertEquals(expectedStatusCode, response.getStatus(), "Status code");
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
            Path path = File.createTempFile("junit", null, folder).toPath();
            KeyStoreUtils.writeKeyStoreToFile(keyStore, path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
