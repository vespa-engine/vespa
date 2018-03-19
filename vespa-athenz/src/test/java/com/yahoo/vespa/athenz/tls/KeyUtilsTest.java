package com.yahoo.vespa.athenz.tls;

import org.junit.Test;

import java.security.KeyPair;
import java.security.PublicKey;

import static org.junit.Assert.assertNotNull;

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

}