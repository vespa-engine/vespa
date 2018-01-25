// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class CsrSerializedPayloadTest {

    @Test
    public void it_can_be_deserialized() throws IOException {
        String serialized = "{\"csr\":\"-----BEGIN CERTIFICATE REQUEST-----\\nMIICVDCCATwCAQAwDzENMAsGA1UEAwwEdGV" +
                "zdDCCASIwDQYJKoZIhvcNAQEBBQAD\\nggEPADCCAQoCggEBAL7xra4De9B54yY6lw8Ka/lt7lDEKQRp42RYzpXjHIQXFgr8" +
                "\\n+EvJCLEldFoqfOm728KAWQq/8YdFR4hBwOz8Rr8khJKMBCQ2DWvGYz2705nr3j3v\\nsd3RE5i8n8cUdKiHRuOf305xgy" +
                "970TFb+s5/tQOfDMDfvC/BdHNhB4pc0P04CVs/\\nzusKvghdSXFVufAuVaY30ZyviqrDVlBZnI158MmRzfINwP70ZYn5wsq" +
                "crKzgSUBp\\nH/WjxaklSzGOH8Uk/EKVx0luzAxtTU8jO7MU1+EG8H4E+FI9ijdjftYyko5UAOQO\\nJGiI9/qHJIMVOIcQa" +
                "k1PA5+2/0NbtVxihQi/uJcCAwEAAaAAMA0GCSqGSIb3DQEB\\nCwUAA4IBAQAelFvM6PyDFufv9pNmFigNqOO+r8ats9Xak9" +
                "JVtGERo9KFcNDAkawD\\nMPzWQeB87oPnB5dlSdkI2J/jIV7/zR9Qoa2qZlKeL4vUIvfMTj5EOmQLn4ofoBwa\\n50D8Ro3D" +
                "06Ohb1KE3seOK2FfVybiATpoaICCjb0ibhx4lNsJGZXpw6F2OdTRi8Fb\\n7kfgLiLPCH+UiHDeVnjVVr/PUKeSImgv44mb4" +
                "c6EU29MYkM4LxCY9/c4scG7Pq+s\\nuHU5Tepjsnmkdtip5NzS7csPXENEygKyksPHWFFojPrtF6nFkMzzIPUgKbsmm4+H\\" +
                "nfJihCYL3pc3+bVYl87TIcdohJ1GYvfw7\\n-----END CERTIFICATE REQUEST-----\\n\"}";
        CsrSerializedPayload csrSerializedPayload = Utils.getMapper().readValue(serialized, CsrSerializedPayload.class);
        assertThat(csrSerializedPayload.csr, notNullValue());
    }

}
