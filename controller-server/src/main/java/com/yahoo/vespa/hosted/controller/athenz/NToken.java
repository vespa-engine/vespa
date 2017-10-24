// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

import com.yahoo.athenz.auth.token.PrincipalToken;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * Represents an Athenz NToken (principal token)
 *
 * @author bjorncs
 */
// TODO Split out encoding/decoding of token into separate class. Move NToken to controller-api.
public class NToken {

    // Max allowed skew in token timestamp (only for creation, not expiry timestamp)
    private static final int ALLOWED_TIMESTAMP_OFFSET = (int) TimeUnit.SECONDS.toSeconds(300);

    private final PrincipalToken token;

    // Note: PrincipalToken does not provide any way of constructing an instance from a unsigned token string
    public NToken(String signedToken) {
        try {
            this.token = new PrincipalToken(signedToken);
            if (this.token.getSignature() == null) {
                throw new IllegalArgumentException("Signature missing (unsigned token)");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed NToken: " + e.getMessage());
        }
    }

    public AthenzPrincipal getPrincipal() {
        return new AthenzPrincipal(getDomain(), getUser());
    }

    public UserId getUser() {
        return new UserId(token.getName());
    }

    public AthenzDomain getDomain() {
        return new AthenzDomain(token.getDomain());
    }

    public String getToken() {
        return token.getSignedToken();
    }

    public String getKeyId() {
        return token.getKeyId();
    }

    public void validateSignatureAndExpiration(PublicKey publicKey) throws InvalidTokenException {
        StringBuilder errorMessageBuilder = new StringBuilder();
        if (!token.validate(publicKey, ALLOWED_TIMESTAMP_OFFSET, true, errorMessageBuilder)) {
            throw new InvalidTokenException("NToken is expired or has invalid signature: " + errorMessageBuilder.toString());
        }
    }

    @Override
    public String toString() {
        return String.format("NToken(%s)", getToken());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NToken nToken = (NToken) o;
        return Objects.equals(getToken(), nToken.getToken()); // PrincipalToken does not implement equals()
    }

    @Override
    public int hashCode() {
        return Objects.hash(getToken()); // PrincipalToken does not implement hashcode()
    }

    public static class Builder {

        private final String version;
        private final AthenzPrincipal principal;
        private final PrivateKey privateKey;
        private final String keyId;
        private Optional<String> salt = Optional.empty();
        private Optional<String> hostname = Optional.empty();
        private Optional<String> ip = Optional.empty();
        private OptionalLong issueTime = OptionalLong.empty();
        private OptionalLong expirationWindow = OptionalLong.empty();

        /**
         * NOTE: We must have some signature, else we might end up with problems later on as
         * {@link PrincipalToken#PrincipalToken(String)} only accepts signed token
         * (supplying an unsigned token to the constructor will result in inconsistent state)
         */
        public Builder(String version, AthenzPrincipal principal, PrivateKey privateKey, String keyId) {
            this.version = version;
            this.principal = principal;
            this.privateKey = privateKey;
            this.keyId = keyId;
        }

        public Builder salt(String salt) {
            this.salt = Optional.of(salt);
            return this;
        }

        public Builder hostname(String hostname) {
            this.hostname = Optional.of(hostname);
            return this;
        }

        public Builder ip(String ip) {
            this.ip = Optional.of(ip);
            return this;
        }

        public Builder issueTime(long issueTime) {
            this.issueTime = OptionalLong.of(issueTime);
            return this;
        }

        public Builder expirationWindow(long expirationWindow) {
            this.expirationWindow = OptionalLong.of(expirationWindow);
            return this;
        }

        public NToken build() {
            PrincipalToken token = new PrincipalToken.Builder(version, principal.getDomain().id(), principal.getName())
                    .keyId(this.keyId)
                    .salt(this.salt.orElse(null))
                    .host(this.hostname.orElse(null))
                    .ip(this.ip.orElse(null))
                    .issueTime(this.issueTime.orElse(0))
                    .expirationWindow(this.expirationWindow.orElse(0))
                    .build();
            token.sign(this.privateKey);
            return new NToken(token.getSignedToken());
        }
    }

}
