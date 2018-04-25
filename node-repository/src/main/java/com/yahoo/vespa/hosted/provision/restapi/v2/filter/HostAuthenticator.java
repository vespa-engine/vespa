// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.tls.SubjectAlternativeName;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.security.cert.X509Certificate;
import java.util.List;

import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.DNS_NAME;

/**
 * Resolve node from various types of x509 identity certificates.
 *
 * @author bjorncs
 */
class HostAuthenticator {

    private static final String TENANT_DOCKER_HOST_IDENTITY = "vespa.vespa.tenant-host";
    private static final String TENANT_DOCKER_CONTAINER_IDENTITY = "vespa.vespa.tenant";
    private static final String INSTANCE_ID_DELIMITER = ".instanceid.athenz.";

    private final Zone zone;
    private final NodeRepository nodeRepository;

    HostAuthenticator(Zone zone, NodeRepository nodeRepository) {
        this.zone = zone;
        this.nodeRepository = nodeRepository;
    }

    NodePrincipal authenticate(List<X509Certificate> certificateChain) throws AuthenticationException {
        X509Certificate clientCertificate = certificateChain.get(0);
        String subjectCommonName = X509CertificateUtils.getSubjectCommonNames(clientCertificate).stream()
                .findFirst()
                .orElseThrow(() -> new AuthenticationException("Certificate subject common name is missing!"));
        if (isAthenzIssued(clientCertificate)) {
            List<SubjectAlternativeName> sans = X509CertificateUtils.getSubjectAlternativeNames(clientCertificate);
            switch (subjectCommonName) {
                case TENANT_DOCKER_HOST_IDENTITY:
                    return NodePrincipal.withAthenzIdentity(subjectCommonName, getHostFromCalypsoOrAwsCertificate(sans), certificateChain);
                case TENANT_DOCKER_CONTAINER_IDENTITY:
                    return NodePrincipal.withAthenzIdentity(subjectCommonName, getHostFromVespaCertificate(sans), certificateChain);
                default:
                    return NodePrincipal.withAthenzIdentity(subjectCommonName, certificateChain);
            }
        } else { // self-signed where common name is hostname
            // TODO Remove this branch once self-signed certificates are gone
            return NodePrincipal.withLegacyIdentity(subjectCommonName, certificateChain);
        }
    }

    private boolean isAthenzIssued(X509Certificate certificate) {
        String issuerCommonName = X509CertificateUtils.getIssuerCommonNames(certificate).stream()
                .findFirst()
                .orElseThrow(() -> new AuthenticationException("Certificate issuer common name is missing!"));
        return issuerCommonName.equals("Yahoo Athenz CA") || issuerCommonName.equals("Athenz AWS CA");
    }

    // NOTE: AWS instance id is currently stored as the attribute 'openstack-id' in node repository.
    private String getHostFromCalypsoOrAwsCertificate(List<SubjectAlternativeName> sans) {
        return getHostFromCalypsoCertificate(sans);
    }

    private String getHostFromCalypsoCertificate(List<SubjectAlternativeName> sans) {
        String openstackId = getUniqueInstanceId(sans);
        return nodeRepository.getNodes().stream()
                .filter(node -> node.openStackId().equals(openstackId))
                .map(Node::hostname)
                .findFirst()
                .orElseThrow(() -> new AuthenticationException(String.format("Cannot find node with openstack-id '%s' in node repository", openstackId)));
    }

    private String getHostFromVespaCertificate(List<SubjectAlternativeName> sans) {
        VespaUniqueInstanceId instanceId = VespaUniqueInstanceId.fromDottedString(getUniqueInstanceId(sans));
        if (!zone.environment().value().equals(instanceId.environment()))
            throw new AuthenticationException("Invalid environment: " + instanceId.environment());
        if (!zone.region().value().equals(instanceId.region()))
            throw new AuthenticationException("Invalid region(): " + instanceId.region());
        List<Node> applicationNodes =
                nodeRepository.getNodes(ApplicationId.from(instanceId.tenant(), instanceId.application(), instanceId.instance()));
        return applicationNodes.stream()
                .filter(
                        node -> node.allocation()
                                .map(allocation -> allocation.membership().index() == instanceId.clusterIndex()
                                        && allocation.membership().cluster().id().value().equals(instanceId.clusterId()))
                                .orElse(false))
                .map(Node::hostname)
                .findFirst()
                .orElseThrow(() -> new AuthenticationException("Could not find any node with instance id: " + instanceId.asDottedString()));
    }

    private static String getUniqueInstanceId(List<SubjectAlternativeName> sans) {
        return sans.stream()
                .filter(san -> san.getType() == DNS_NAME)
                .map(SubjectAlternativeName::getValue)
                .filter(dnsName -> (dnsName.endsWith("yahoo.cloud") || dnsName.endsWith("oath.cloud")) && dnsName.contains(INSTANCE_ID_DELIMITER))
                .map(dnsName -> dnsName.substring(0, dnsName.indexOf(INSTANCE_ID_DELIMITER)))
                .findFirst()
                .orElseThrow(() -> new AuthenticationException("Could not find unique instance id from SAN addresses: " + sans));
    }

    static class AuthenticationException extends RuntimeException {
        AuthenticationException(String message) {
            super(message);
        }
    }

}
