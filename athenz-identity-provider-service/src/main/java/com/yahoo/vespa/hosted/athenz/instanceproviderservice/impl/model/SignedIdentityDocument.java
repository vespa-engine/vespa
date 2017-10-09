// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Objects;

/**
 * @author bjorncs
 */
public class SignedIdentityDocument {

    @JsonProperty("identity-document")public final String rawIdentityDocument;
    @JsonIgnore public final IdentityDocument identityDocument;
    @JsonProperty("signature") public final String signature;
    @JsonProperty("signing-key-version") public final int signingKeyVersion;
    @JsonProperty("document-version") public final int documentVersion;

    @JsonCreator
    public SignedIdentityDocument(@JsonProperty("identity-document") String rawIdentityDocument,
                                  @JsonProperty("signature") String signature,
                                  @JsonProperty("signing-key-version") int signingKeyVersion,
                                  @JsonProperty("document-version") int documentVersion) {
        this.rawIdentityDocument = rawIdentityDocument;
        this.identityDocument = parseIdentityDocument(rawIdentityDocument);
        this.signature = signature;
        this.signingKeyVersion = signingKeyVersion;
        this.documentVersion = documentVersion;
    }

    private static IdentityDocument parseIdentityDocument(String rawIdentityDocument) {
        try {
            return Utils.getMapper().readValue(Base64.getDecoder().decode(rawIdentityDocument), IdentityDocument.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return "SignedIdentityDocument{" +
                "rawIdentityDocument='" + rawIdentityDocument + '\'' +
                ", identityDocument=" + identityDocument +
                ", signature='" + signature + '\'' +
                ", signingKeyVersion=" + signingKeyVersion +
                ", documentVersion=" + documentVersion +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignedIdentityDocument that = (SignedIdentityDocument) o;
        return signingKeyVersion == that.signingKeyVersion &&
                documentVersion == that.documentVersion &&
                Objects.equals(rawIdentityDocument, that.rawIdentityDocument) &&
                Objects.equals(identityDocument, that.identityDocument) &&
                Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawIdentityDocument, identityDocument, signature, signingKeyVersion, documentVersion);
    }
}
