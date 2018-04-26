package com.yahoo.vespa.athenz.api.bindings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableSet;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.IdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.ProviderUniqueId;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;

import static org.junit.Assert.assertEquals;

public class IdentityDocumentTest {

    @Test
    public void test_serialization_deserialization() throws IOException {
        IdentityDocument document = new IdentityDocument(
                ProviderUniqueId.fromVespaUniqueInstanceId(
                        VespaUniqueInstanceId.fromDottedString("1.clusterId.instance.application.tenant.region.environment")),
                "cfg.prod.xyz",
                "foo.bar",
                Instant.now(),
                ImmutableSet.of("127.0.0.1", "::1"));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String documentString = mapper.writeValueAsString(document);
        IdentityDocument deserializedDocument = mapper.readValue(documentString, IdentityDocument.class);
        assertEquals(document, deserializedDocument);
    }
}
