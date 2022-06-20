// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.ssl.ConfiguredFilebasedSslProvider;
import com.yahoo.vespa.model.container.http.ssl.CustomSslProvider;
import com.yahoo.vespa.model.container.http.ssl.DefaultSslProvider;
import com.yahoo.vespa.model.container.http.ssl.SslProvider;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * @author Einar M R Rosenvinge
 * @author mortent
 */
public class JettyConnectorBuilder extends VespaDomBuilder.DomConfigProducerBuilder<ConnectorFactory>  {

    @Override
    protected ConnectorFactory doBuild(DeployState deployState, AbstractConfigProducer<?> ancestor, Element serverSpec) {
        String name = XmlHelper.getIdString(serverSpec);
        int port = HttpBuilder.readPort(new ModelElement(serverSpec), deployState.isHosted());
        ConnectorFactory.Builder builder = new ConnectorFactory.Builder(name, port);
        XmlHelper.getOptionalAttribute(serverSpec, "default-request-chain")
                .map(ComponentId::new)
                .ifPresent(builder::defaultRequestFilterChain);
        XmlHelper.getOptionalAttribute(serverSpec, "default-response-chain")
                .map(ComponentId::new)
                .ifPresent(builder::defaultResponseFilterChain);
        SslProvider sslProviderComponent = getSslConfigComponents(name, serverSpec);
        return builder.sslProvider(sslProviderComponent).build();
    }

    SslProvider getSslConfigComponents(String serverName, Element serverSpec) {
        Element sslConfigurator = XML.getChild(serverSpec, "ssl");
        Element sslProviderConfigurator = XML.getChild(serverSpec, "ssl-provider");

        if (sslConfigurator != null) {
            String privateKeyFile = XML.getValue(XML.getChild(sslConfigurator, "private-key-file"));
            String certificateFile = XML.getValue(XML.getChild(sslConfigurator, "certificate-file"));
            Optional<String> caCertificateFile = XmlHelper.getOptionalChildValue(sslConfigurator, "ca-certificates-file");
            Optional<String> clientAuthentication = XmlHelper.getOptionalChildValue(sslConfigurator, "client-authentication");
            List<String> cipherSuites = extractOptionalCommaSeparatedList(sslConfigurator, "cipher-suites");
            List<String> protocols = extractOptionalCommaSeparatedList(sslConfigurator, "protocols");
            return new ConfiguredFilebasedSslProvider(
                    serverName,
                    privateKeyFile,
                    certificateFile,
                    caCertificateFile.orElse(null),
                    clientAuthentication.orElse(null),
                    cipherSuites,
                    protocols);
        } else if (sslProviderConfigurator != null) {
            String className = sslProviderConfigurator.getAttribute("class");
            String bundle = sslProviderConfigurator.getAttribute("bundle");
            return new CustomSslProvider(serverName, className, bundle);
        } else {
            return new DefaultSslProvider(serverName);
        }
    }

    private static List<String> extractOptionalCommaSeparatedList(Element sslElement, String listElementName) {
        return XmlHelper.getOptionalChildValue(sslElement, listElementName)
                .map(element ->
                             Arrays.stream(element.split(","))
                                     .filter(listEntry -> !listEntry.isBlank())
                                     .map(String::trim)
                                     .collect(toList()))
                .orElse(List.of());
    }
}
