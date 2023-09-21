package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.text.XML;
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
                if (id.isEmpty()) throw new IllegalArgumentException(CONTAINER_TAG + " tag requires 'id' attribute");
                List<Container.AuthMethod> methods = parseAuthMethods(childNode);
                containers.add(new Container(id, methods));
            }
        }
        return new BasicServicesXml(containers);
    }

    private static List<BasicServicesXml.Container.AuthMethod> parseAuthMethods(Element containerNode) {
        List<BasicServicesXml.Container.AuthMethod> methods = new ArrayList<>();
        for (var node : XML.getChildren(containerNode)) {
            if (node.getTagName().equals(CLIENTS_TAG)) {
                for (var clientNode : XML.getChildren(node)) {
                    if (clientNode.getTagName().equals(CLIENT_TAG)) {
                        boolean tokenEnabled = XML.getChildren(clientNode).stream()
                                                  .anyMatch(n -> n.getTagName().equals(TOKEN_TAG));
                        methods.add(tokenEnabled ? Container.AuthMethod.token : Container.AuthMethod.mtls);
                    }
                }
            }
        }
        if (methods.isEmpty()) {
            methods.add(Container.AuthMethod.mtls);
        }
        return methods;
    }

    /**
     * A Vespa container service.
     *
     * @param id          ID of container
     * @param authMethods Authentication methods supported by this container
     */
    public record Container(String id, List<AuthMethod> authMethods) {

        public Container(String id, List<AuthMethod> authMethods) {
            this.id = Objects.requireNonNull(id);
            this.authMethods = Objects.requireNonNull(authMethods).stream()
                                      .distinct()
                                      .sorted()
                                      .toList();
            if (authMethods.isEmpty()) throw new IllegalArgumentException("Container must have at least one auth method");
        }

        public enum AuthMethod {
            mtls,
            token,
        }

    }

}
