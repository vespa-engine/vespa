// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * InstanceConfirmation object as per Athenz InstanceConfirmation API.
 *
 * @author bjorncs
 */
public class InstanceConfirmation {

    @JsonProperty("provider") public final String provider;
    @JsonProperty("domain") public final String domain;
    @JsonProperty("service") public final String service;

    @JsonProperty("attestationData") @JsonSerialize(using = SignedIdentitySerializer.class)
    public final SignedIdentityDocumentEntity signedIdentityDocument;
    @JsonUnwrapped public final Map<String, Object> attributes = new HashMap<>(); // optional attributes that Athenz may provide

    @JsonCreator
    public InstanceConfirmation(@JsonProperty("provider") String provider,
                                @JsonProperty("domain") String domain,
                                @JsonProperty("service") String service,
                                @JsonProperty("attestationData") @JsonDeserialize(using = SignedIdentityDeserializer.class)
                                            SignedIdentityDocumentEntity signedIdentityDocument) {
        this.provider = provider;
        this.domain = domain;
        this.service = service;
        this.signedIdentityDocument = signedIdentityDocument;
    }

    @JsonAnySetter
    public void set(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public String toString() {
        return "InstanceConfirmation{" +
                "provider='" + provider + '\'' +
                ", domain='" + domain + '\'' +
                ", service='" + service + '\'' +
                ", signedIdentityDocument='" + signedIdentityDocument + '\'' +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceConfirmation that = (InstanceConfirmation) o;
        return Objects.equals(provider, that.provider) &&
                Objects.equals(domain, that.domain) &&
                Objects.equals(service, that.service) &&
                Objects.equals(signedIdentityDocument, that.signedIdentityDocument) &&
                Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, domain, service, signedIdentityDocument, attributes);
    }

    public static class SignedIdentityDeserializer extends JsonDeserializer<SignedIdentityDocumentEntity> {
        @Override
        public SignedIdentityDocumentEntity deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            String value = jsonParser.getValueAsString();
            return Utils.getMapper().readValue(value, SignedIdentityDocumentEntity.class);
        }
    }

    public static class SignedIdentitySerializer extends JsonSerializer<SignedIdentityDocumentEntity> {
        @Override
        public void serialize(
                SignedIdentityDocumentEntity document, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(Utils.getMapper().writeValueAsString(document));
        }
    }
}
