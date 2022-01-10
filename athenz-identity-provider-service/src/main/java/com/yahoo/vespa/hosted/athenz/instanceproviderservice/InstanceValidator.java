// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.common.net.InetAddresses;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.identityprovider.client.IdentityDocumentSigner;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.net.InetAddress;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Verifies that the instance's identity document is valid
 *
 * @author bjorncs
 * @author mortent
 */
public class InstanceValidator {

    private static final Logger log = Logger.getLogger(InstanceValidator.class.getName());
    static final String SERVICE_PROPERTIES_DOMAIN_KEY = "identity.domain";
    static final String SERVICE_PROPERTIES_SERVICE_KEY = "identity.service";
    static final String INSTANCE_ID_DELIMITER = ".instanceid.athenz.";

    public static final String SAN_IPS_ATTRNAME = "sanIP";
    public static final String SAN_DNS_ATTRNAME = "sanDNS";

    private final AthenzService tenantDockerContainerIdentity;
    private final IdentityDocumentSigner signer;
    private final KeyProvider keyProvider;
    private final SuperModelProvider superModelProvider;
    private final NodeRepository nodeRepository;

    @Inject
    public InstanceValidator(KeyProvider keyProvider,
                             SuperModelProvider superModelProvider,
                             NodeRepository nodeRepository,
                             AthenzProviderServiceConfig config) {
        this(keyProvider, superModelProvider, nodeRepository, new IdentityDocumentSigner(), new AthenzService(config.tenantService()));
    }

    public InstanceValidator(KeyProvider keyProvider,
                             SuperModelProvider superModelProvider,
                             NodeRepository nodeRepository,
                             IdentityDocumentSigner identityDocumentSigner,
                             AthenzService tenantIdentity){
        this.keyProvider = keyProvider;
        this.superModelProvider = superModelProvider;
        this.nodeRepository = nodeRepository;
        this.signer = identityDocumentSigner;
        this.tenantDockerContainerIdentity = tenantIdentity;
    }

    public boolean isValidInstance(InstanceConfirmation instanceConfirmation) {
        SignedIdentityDocument signedIdentityDocument = EntityBindingsMapper.toSignedIdentityDocument(instanceConfirmation.signedIdentityDocument);
        VespaUniqueInstanceId providerUniqueId = signedIdentityDocument.providerUniqueId();
        ApplicationId applicationId = ApplicationId.from(
                providerUniqueId.tenant(), providerUniqueId.application(), providerUniqueId.instance());

        VespaUniqueInstanceId csrProviderUniqueId = getVespaUniqueInstanceId(instanceConfirmation);
        if(! providerUniqueId.equals(csrProviderUniqueId)) {
            log.log(Level.WARNING, String.format("Instance %s has invalid provider unique ID in CSR (%s)", providerUniqueId, csrProviderUniqueId));
            return false;
        }

        if (! isSameIdentityAsInServicesXml(applicationId, instanceConfirmation.domain, instanceConfirmation.service)) {
            return false;
        }

        log.log(Level.FINE, () -> String.format("Validating instance %s.", providerUniqueId));

        PublicKey publicKey = keyProvider.getPublicKey(signedIdentityDocument.signingKeyVersion());
        if (! signer.hasValidSignature(signedIdentityDocument, publicKey)) {
            log.log(Level.SEVERE, () -> String.format("Instance %s has invalid signature.", providerUniqueId));
            return false;
        }

        if(validateAttributes(instanceConfirmation, providerUniqueId)) {
            log.log(Level.FINE, () -> String.format("Instance %s is valid.", providerUniqueId));
            return true;
        }
        return false;
    }

    // TODO Add actual validation. Cannot reuse isValidInstance as identity document is not part of the refresh request.
    //      We'll have to perform some validation on the instance id and other fields of the attribute map.
    //      Separate between tenant and node certificate as well.
    public boolean isValidRefresh(InstanceConfirmation confirmation) {
        log.log(Level.FINE, () -> String.format("Accepting refresh for instance with identity '%s', provider '%s', instanceId '%s'.",
                                                   new AthenzService(confirmation.domain, confirmation.service).getFullName(),
                                                   confirmation.provider,
                                                   confirmation.attributes.get(SAN_DNS_ATTRNAME)));
        try {
            return validateAttributes(confirmation, getVespaUniqueInstanceId(confirmation));
        } catch (Exception e) {
            log.log(Level.WARNING, "Encountered exception while refreshing certificate for confirmation: " + confirmation, e);
            return false;
        }
    }

    private VespaUniqueInstanceId getVespaUniqueInstanceId(InstanceConfirmation instanceConfirmation) {
        // Find a list of SAN DNS
        List<String> sanDNS = Optional.ofNullable(instanceConfirmation.attributes.get(SAN_DNS_ATTRNAME))
                .map(s -> s.split(","))
                .map(Arrays::asList)
                .map(List::stream)
                .orElse(Stream.empty())
                .collect(Collectors.toList());

        return sanDNS.stream()
                .filter(dns -> dns.contains(INSTANCE_ID_DELIMITER))
                .findFirst()
                .map(s -> s.replaceAll(INSTANCE_ID_DELIMITER + ".*", ""))
                .map(VespaUniqueInstanceId::fromDottedString)
                .orElse(null);
    }

    private boolean validateAttributes(InstanceConfirmation confirmation, VespaUniqueInstanceId vespaUniqueInstanceId) {
        if(vespaUniqueInstanceId == null) {
            log.log(Level.WARNING, "Unable to find unique instance ID in refresh request: " + confirmation.toString());
            return false;
        }

        // Find node matching vespa unique id
        Node node = nodeRepository.nodes().list().stream()
                .filter(n -> n.allocation().isPresent())
                .filter(n -> nodeMatchesVespaUniqueId(n, vespaUniqueInstanceId))
                .findFirst() // Should be only one
                .orElse(null);
        if(node == null) {
            log.log(Level.WARNING, "Invalid InstanceConfirmation, No nodes matching uniqueId: " + vespaUniqueInstanceId);
            return false;
        }

        // Find list of ipaddresses
        List<InetAddress> ips = Optional.ofNullable(confirmation.attributes.get(SAN_IPS_ATTRNAME))
                .map(s -> s.split(","))
                .map(Arrays::asList)
                .map(List::stream)
                .orElse(Stream.empty())
                .map(InetAddresses::forString)
                .collect(Collectors.toList());

        List<InetAddress> nodeIpAddresses = node.ipConfig().primary().stream()
                                                .map(InetAddresses::forString)
                                                .collect(Collectors.toList());

        // Validate that ipaddresses in request are valid for node

        if(! nodeIpAddresses.containsAll(ips)) {
            log.log(Level.WARNING, "Invalid InstanceConfirmation, wrong ip in : " + vespaUniqueInstanceId);
            return false;
        }
        return true;
    }

    private boolean nodeMatchesVespaUniqueId(Node node, VespaUniqueInstanceId vespaUniqueInstanceId) {
        return node.allocation().map(allocation ->
                                             allocation.membership().index() == vespaUniqueInstanceId.clusterIndex() &&
                                             allocation.membership().cluster().id().value().equals(vespaUniqueInstanceId.clusterId()) &&
                                             allocation.owner().instance().value().equals(vespaUniqueInstanceId.instance()) &&
                                             allocation.owner().application().value().equals(vespaUniqueInstanceId.application()) &&
                                             allocation.owner().tenant().value().equals(vespaUniqueInstanceId.tenant()))
                .orElse(false);
    }

    // If/when we don't care about logging exactly whats wrong, this can be simplified
    // TODO Use identity type to determine if this check should be performed
    private boolean isSameIdentityAsInServicesXml(ApplicationId applicationId, String domain, String service) {

        Optional<ApplicationInfo> applicationInfo = superModelProvider.getSuperModel().getApplicationInfo(applicationId);

        if (!applicationInfo.isPresent()) {
            log.info(String.format("Could not find application info for %s, existing applications: %s",
                                   applicationId.serializedForm(),
                                   superModelProvider.getSuperModel().getAllApplicationInfos()));
            return false;
        }

        if (tenantDockerContainerIdentity.equals(new AthenzService(domain, service))) {
            return true;
        }

        Optional<ServiceInfo> matchingServiceInfo = applicationInfo.get()
                .getModel()
                .getHosts()
                .stream()
                .flatMap(hostInfo -> hostInfo.getServices().stream())
                .filter(serviceInfo -> serviceInfo.getProperty(SERVICE_PROPERTIES_DOMAIN_KEY).isPresent())
                .filter(serviceInfo -> serviceInfo.getProperty(SERVICE_PROPERTIES_SERVICE_KEY).isPresent())
                .findFirst();

        if (!matchingServiceInfo.isPresent()) {
            log.info(String.format("Application %s has not specified domain/service", applicationId.serializedForm()));
            return false;
        }

        String domainInConfig = matchingServiceInfo.get().getProperty(SERVICE_PROPERTIES_DOMAIN_KEY).get();
        String serviceInConfig = matchingServiceInfo.get().getProperty(SERVICE_PROPERTIES_SERVICE_KEY).get();
        if (!domainInConfig.equals(domain) || !serviceInConfig.equals(service)) {
            log.warning(String.format("domain '%s' or service '%s' does not match the one in config for application %s",
                    domain, service, applicationId.serializedForm()));
            return false;
        }

        return true;
    }
}
