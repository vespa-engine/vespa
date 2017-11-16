// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.CertificateSigner;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Contains PEM formatted signed certificate
 *
 * @author freva
 */
public class CertificateSerializedPayload {

    @JsonProperty("certificate") @JsonSerialize(using = CertificateSerializer.class)
    public final X509Certificate certificate;

    @JsonCreator
    public CertificateSerializedPayload(@JsonProperty("certificate") @JsonDeserialize(using = CertificateDeserializer.class)
                                                    X509Certificate certificate) {
        this.certificate = certificate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CertificateSerializedPayload that = (CertificateSerializedPayload) o;

        return certificate.equals(that.certificate);
    }

    @Override
    public int hashCode() {
        return certificate.hashCode();
    }

    @Override
    public String toString() {
        return "CertificateSerializedPayload{" +
                "certificate='" + certificate + '\'' +
                '}';
    }

    private static class CertificateSerializer extends JsonSerializer<X509Certificate> {
        @Override
        public void serialize(
                X509Certificate certificate, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
                pemWriter.writeObject(new PemObject("CERTIFICATE", certificate.getEncoded()));
                pemWriter.flush();
                gen.writeString(stringWriter.toString());
            } catch (CertificateEncodingException e) {
                throw new RuntimeException("Failed to encode X509Certificate", e);
            }
        }
    }

    private static class CertificateDeserializer extends JsonDeserializer<X509Certificate> {
        @Override
        public X509Certificate deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            try (PEMParser pemParser = new PEMParser(new StringReader(jsonParser.getValueAsString()))) {
                Object pemObject = pemParser.readObject();
                return CertificateSigner.CERTIFICATE_CONVERTER.getCertificate((X509CertificateHolder) pemObject);
            } catch (CertificateException e) {
                throw new IllegalArgumentException("Unable to convert certificate", e);
            }
        }
    }
}
