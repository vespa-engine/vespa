package com.yahoo.vespa.athenz.tls;

import org.junit.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class KeyUtilsTest {

    @Test
    public void can_extract_public_key_from_private() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        PublicKey publicKey = KeyUtils.extractPublicKey(keyPair.getPrivate());
        assertNotNull(publicKey);
    }

    @Test
    public void can_serialize_deserialize_pem() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        String pem = KeyUtils.toPem(keyPair.getPrivate());
        assertThat(pem, containsString("BEGIN PRIVATE KEY"));
        assertThat(pem, containsString("END PRIVATE KEY"));
        PrivateKey deserializedKey = KeyUtils.fromPemEncodedPrivateKey(pem);
        assertEquals(keyPair.getPrivate(), deserializedKey);
    }

}