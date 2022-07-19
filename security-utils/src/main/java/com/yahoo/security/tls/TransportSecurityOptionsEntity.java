// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Jackson bindings for transport security options
 *
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class TransportSecurityOptionsEntity {

    @JsonProperty("files") Files files;
    @JsonProperty("authorized-peers") @JsonInclude(NON_EMPTY) List<AuthorizedPeer> authorizedPeers;
    @JsonProperty("accepted-ciphers") @JsonInclude(NON_EMPTY) List<String> acceptedCiphers;
    @JsonProperty("accepted-protocols") @JsonInclude(NON_EMPTY) List<String> acceptedProtocols;
    @JsonProperty("disable-hostname-validation") @JsonInclude(NON_NULL) Boolean isHostnameValidationDisabled;

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Files {
        @JsonProperty("private-key") String privateKeyFile;
        @JsonProperty("certificates") String certificatesFile;
        @JsonProperty("ca-certificates") String caCertificatesFile;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AuthorizedPeer {
        @JsonProperty("required-credentials") List<RequiredCredential> requiredCredentials;
        @JsonProperty("name") String name;
        @JsonProperty("description") @JsonInclude(NON_NULL) String description;
        @JsonProperty("capabilities") @JsonInclude(NON_EMPTY) List<String> capabilities;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RequiredCredential {
        @JsonProperty("field") CredentialField field;
        @JsonProperty("must-match") String matchExpression;
    }

    enum CredentialField { CN, SAN_DNS, SAN_URI }
}
