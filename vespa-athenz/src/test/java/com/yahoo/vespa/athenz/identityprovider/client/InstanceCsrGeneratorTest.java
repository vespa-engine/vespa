// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.security.Pkcs10Csr;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mortent
 */
public class InstanceCsrGeneratorTest {

    private static final String DNS_SUFFIX = "prod-us-north-1.vespa.yahoo.cloud";
    private static final String PROVIDER_SERVICE = "vespa.vespa.provider_prod_us-north-1";
    private static final String ATHENZ_SERVICE = "foo.bar";

    @Test
    void it_generates_csr_with_correct_subject() {
        CsrGenerator csrGenerator = new CsrGenerator(DNS_SUFFIX, PROVIDER_SERVICE);

        AthenzService service = new AthenzService(ATHENZ_SERVICE);
        VespaUniqueInstanceId vespaUniqueInstanceId = VespaUniqueInstanceId.fromDottedString("0.default.default.foo-app.vespa.us-north-1.prod.node");
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);

        Pkcs10Csr csr = csrGenerator.generateInstanceCsr(service, vespaUniqueInstanceId, Collections.emptySet(), keyPair);
        assertEquals(new X500Principal(String.format("OU=%s, CN=%s", PROVIDER_SERVICE, ATHENZ_SERVICE)), csr.getSubject());
    }
}
