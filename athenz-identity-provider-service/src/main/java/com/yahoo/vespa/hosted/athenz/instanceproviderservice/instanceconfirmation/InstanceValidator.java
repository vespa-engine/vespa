// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.identityprovider.client.IdentityDocumentSigner;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.KeyProvider;

import java.security.PublicKey;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Verifies that the instance's identity document is valid
 *
 * @author bjorncs
 */
public class InstanceValidator {

    private static final AthenzService TENANT_DOCKER_CONTAINER_IDENTITY = new AthenzService("vespa.vespa.tenant");
    private static final Logger log = Logger.getLogger(InstanceValidator.class.getName());
    static final String SERVICE_PROPERTIES_DOMAIN_KEY = "identity.domain";
    static final String SERVICE_PROPERTIES_SERVICE_KEY = "identity.service";

    private final IdentityDocumentSigner signer = new IdentityDocumentSigner();
    private final KeyProvider keyProvider;
    private final SuperModelProvider superModelProvider;

    @Inject
    public InstanceValidator(KeyProvider keyProvider, SuperModelProvider superModelProvider) {
        this.keyProvider = keyProvider;
        this.superModelProvider = superModelProvider;
    }

    public boolean isValidInstance(InstanceConfirmation instanceConfirmation) {
        SignedIdentityDocument signedIdentityDocument = EntityBindingsMapper.toSignedIdentityDocument(instanceConfirmation.signedIdentityDocument);
        VespaUniqueInstanceId providerUniqueId = signedIdentityDocument.providerUniqueId();
        ApplicationId applicationId = ApplicationId.from(
                providerUniqueId.tenant(), providerUniqueId.application(), providerUniqueId.instance());

        if (! isSameIdentityAsInServicesXml(applicationId, instanceConfirmation.domain, instanceConfirmation.service)) {
            return false;
        }

        log.log(LogLevel.INFO, () -> String.format("Validating instance %s.", providerUniqueId));

        PublicKey publicKey = keyProvider.getPublicKey(signedIdentityDocument.signingKeyVersion());
        if (signer.hasValidSignature(signedIdentityDocument, publicKey)) {
            log.log(LogLevel.INFO, () -> String.format("Instance %s is valid.", providerUniqueId));
            return true;
        }
        log.log(LogLevel.ERROR, () -> String.format("Instance %s has invalid signature.", providerUniqueId));
        return false;
    }

    // If/when we dont care about logging exactly whats wrong, this can be simplified
    // TODO Use identity type to determine if this check should be performed
    boolean isSameIdentityAsInServicesXml(ApplicationId applicationId, String domain, String service) {

        Optional<ApplicationInfo> applicationInfo = superModelProvider.getSuperModel().getApplicationInfo(applicationId);

        if (!applicationInfo.isPresent()) {
            log.info(String.format("Could not find application info for %s", applicationId.serializedForm()));
            return false;
        }

        if (TENANT_DOCKER_CONTAINER_IDENTITY.equals(new AthenzService(domain, service))) {
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
