// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.text.XML;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.TokenId;
import com.yahoo.vespa.hosted.controller.application.pkg.BasicServicesXml.Container.AuthMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A partially parsed variant of services.xml, for use by the {@link com.yahoo.vespa.hosted.controller.Controller}.
 *
 * @author mpolden
 */
public record BasicServicesXml(List<Container> containers) {

    public static final BasicServicesXml empty = new BasicServicesXml(List.of());

    private static final String SERVICES_TAG = "services";
    private static final String CONTAINER_TAG = "container";
    private static final String CLIENTS_TAG = "clients";
    private static final String CLIENT_TAG = "client";
    private static final String TOKEN_TAG = "token";

    public BasicServicesXml(List<Container> containers) {
        this.containers = List.copyOf(Objects.requireNonNull(containers));
    }

    /** Parse a services.xml from given document */
    public static BasicServicesXml parse(Document document) {
        Element root = document.getDocumentElement();
        if (!root.getTagName().equals("services")) {
            throw new IllegalArgumentException("Root tag must be <" + SERVICES_TAG + ">");
        }
        List<BasicServicesXml.Container> containers = new ArrayList<>();
        for (var childNode : XML.getChildren(root)) {
            if (childNode.getTagName().equals(CONTAINER_TAG)) {
                String id = childNode.getAttribute("id");
                if (id.isEmpty()) {
                    id = CONTAINER_TAG; // ID defaults to tag name when unset. See ConfigModelBuilder::getIdString
                }
                List<Container.AuthMethod> methods = new ArrayList<>();
                List<TokenId> tokens = new ArrayList<>();
                parseAuthMethods(childNode, methods, tokens);
                containers.add(new Container(id, methods, tokens));
            }
        }
        return new BasicServicesXml(containers);
    }

    private static void parseAuthMethods(Element containerNode, List<AuthMethod> methods, List<TokenId> tokens) {
        for (var node : XML.getChildren(containerNode)) {
            if (node.getTagName().equals(CLIENTS_TAG)) {
                for (var clientNode : XML.getChildren(node)) {
                    if (clientNode.getTagName().equals(CLIENT_TAG)) {
                        boolean tokenEnabled = false;
                        for (var child : XML.getChildren(clientNode)) {
                            if (TOKEN_TAG.equals(child.getTagName())) {
                                tokenEnabled = true;
                                tokens.add(TokenId.of(child.getAttribute("id")));
                            }
                        }
                        methods.add(tokenEnabled ? Container.AuthMethod.token : Container.AuthMethod.mtls);
                    }
                }
            }
        }
        if (methods.isEmpty()) {
            methods.add(Container.AuthMethod.mtls);
        }
    }

    /**
     * A Vespa container service.
     *
     * @param id          ID of container
     * @param authMethods Authentication methods supported by this container
     */
    public record Container(String id, List<AuthMethod> authMethods, List<TokenId> dataPlaneTokens) {

        public Container(String id, List<AuthMethod> authMethods, List<TokenId> dataPlaneTokens) {
            this.id = Objects.requireNonNull(id);
            this.authMethods = Objects.requireNonNull(authMethods).stream()
                                      .distinct()
                                      .sorted()
                                      .toList();
            if (authMethods.isEmpty()) throw new IllegalArgumentException("Container must have at least one auth method");
            this.dataPlaneTokens = dataPlaneTokens.stream().sorted().distinct().toList();
        }

        public enum AuthMethod {
            mtls,
            token,
        }

    }

}
