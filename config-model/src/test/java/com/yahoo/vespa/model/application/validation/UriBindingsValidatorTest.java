// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class UriBindingsValidatorTest {
    Zone cdZone = new Zone(SystemName.cd, Environment.prod, RegionName.defaultName());
    Zone publicCdZone = new Zone(SystemName.PublicCd, Environment.prod, RegionName.defaultName());

    @Test
    void fails_on_user_handler_binding_with_port() throws IOException, SAXException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            runUriBindingValidator(true, createServicesXmlWithHandler("http://*:4443/my-handler"));
        });
        assertTrue(exception.getMessage().contains("For binding 'http://*:4443/my-handler': binding with port is not allowed"));
    }

    @Test
    void non_public_logs_on_user_handler_binding_with_port() throws IOException, SAXException {
        StringBuffer log = new StringBuffer();
        DeployLogger logger = (__, message) -> {
            System.out.println("message = " + message);
            log.append(message).append('\n');
        };
        runUriBindingValidator(true, createServicesXmlWithHandler("http://*:4443/my-handler"), cdZone, logger);
        assertTrue(log.toString().contains("For binding 'http://*:4443/my-handler': binding with port is not allowed"));
    }

    @Test
    void fails_on_user_handler_binding_with_hostname() throws IOException, SAXException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            runUriBindingValidator(true, createServicesXmlWithHandler("http://myhostname/my-handler"));
        });
        assertTrue(exception.getMessage().contains("For binding 'http://myhostname/my-handler': only binding with wildcard ('*') for hostname is allowed"));
    }

    @Test
    void fails_on_user_handler_binding_with_non_http_scheme() throws IOException, SAXException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            runUriBindingValidator(true, createServicesXmlWithHandler("ftp://*/my-handler"));
        });
        assertTrue(exception.getMessage().contains("For binding 'ftp://*/my-handler': only 'http' is allowed as scheme"));
    }

    @Test
    void fails_on_invalid_filter_binding() throws IOException, SAXException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            runUriBindingValidator(true, createServicesXmlWithRequestFilterChain("https://*:4443/my-request-filer-chain"));
        });
        assertTrue(exception.getMessage().contains("For binding 'https://*:4443/my-request-filer-chain': binding with port is not allowed"));
    }

    @Test
    void allows_valid_user_binding() throws IOException, SAXException {
        runUriBindingValidator(true, createServicesXmlWithHandler("http://*/my-handler"));
    }

    void allows_user_binding_with_wildcard_port() throws IOException, SAXException {
        runUriBindingValidator(true, createServicesXmlWithHandler("http://*:*/my-handler"));
    }

    @Test
    void only_restricts_user_bindings_on_hosted() throws IOException, SAXException {
        runUriBindingValidator(false, createServicesXmlWithRequestFilterChain("https://*:4443/my-request-filer-chain"));
    }

    private void runUriBindingValidator(boolean isHosted, String servicesXml) throws IOException, SAXException {
        runUriBindingValidator(new TestProperties().setZone(publicCdZone).setHostedVespa(isHosted), servicesXml, (__, message) -> {});
    }
    private void runUriBindingValidator(boolean isHosted, String servicesXml, Zone zone, DeployLogger deployLogger) throws IOException, SAXException {
        runUriBindingValidator(new TestProperties().setZone(zone).setHostedVespa(isHosted), servicesXml, deployLogger);
    }

    private void runUriBindingValidator(TestProperties testProperties, String servicesXml, DeployLogger deployLogger) throws IOException, SAXException {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(app)
                .deployLogger(deployLogger)
                .zone(testProperties.zone())
                .properties(testProperties)
                .endpoints(Set.of(new ContainerEndpoint("default", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))))
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        ValidationTester.validate(new UriBindingsValidator(), model, deployState);
    }

    private static String createServicesXmlWithHandler(String handlerBinding) {
        return String.join(
                "\n",
                "<services version='1.0'>",
                "  <container id='default' version='1.0'>",
                "    <handler id='custom.Handler'>",
                "      <binding>" + handlerBinding + "</binding>",
                "    </handler>",
                "  </container>",
                "</services>");
    }

    private static String createServicesXmlWithRequestFilterChain(String filterBinding) {
        return String.join(
                "\n",
                "<services version='1.0'>",
                "  <container version='1.0' id='default'>",
                "    <http>",
                "      <server port='8080' id='main' />",
                "      <filtering>",
                "        <request-chain id='myChain'>",
                "          <filter id='myFilter'/>",
                "          <binding>" + filterBinding + "</binding>",
                "        </request-chain>",
                "      </filtering>",
                "    </http>",
                "  </container>",
                "</services>");
    }

}
