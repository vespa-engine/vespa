// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

import com.yahoo.security.KeyUtils;

import java.security.KeyPair;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.util.function.Supplier;

import static com.yahoo.security.ArrayUtils.concat;
import static com.yahoo.security.hpke.Constants.EAE_PRK_LABEL;
import static com.yahoo.security.hpke.Constants.EMPTY_LABEL;
import static com.yahoo.security.hpke.Constants.SHARED_SECRET_LABEL;
import static com.yahoo.security.hpke.LabeledKdfUtils.labeledExpandForSuite;
import static com.yahoo.security.hpke.LabeledKdfUtils.labeledExtractForSuite;

/**
 * KEM implementation using Diffie-Hellman over X25519 curves as the shared
 * secret deriver and HKDF-SHA256 as its key derivation function.
 *
 * HPKE KEM spec: DHKEM(X25519, HKDF-SHA256)
 *
 * @author vekterli
 */
final class DHKemX25519HkdfSha256 implements Kem {

    private static final HkdfSha256 HKDF = HkdfSha256.getInstance();

    private final Supplier<KeyPair> keyPairGen;

    DHKemX25519HkdfSha256(Supplier<KeyPair> keyPairGen) {
        this.keyPairGen = keyPairGen;
    }

    // Section 4.1 DH-Based KEM (DHKEM):
    // "The implicit suite_id value used within LabeledExtract and LabeledExpand
    //  is defined as follows, where kem_id is defined in Section 7.1:"
    //
    //    suite_id = concat("KEM", I2OSP(kem_id, 2))"
    //
    // The ID of our KEM suite, DHKEM(X25519, HKDF-SHA256), is 0x0020, so just hard code this.
    private static final byte[] DHKEM_SUITE_ID_LABEL = new byte[] { 'K','E','M', 0x00, 0x20 };

    @Override public short nSecret() { return 32; }
    @Override public short nEnc()    { return 32; }
    @Override public short nPk()     { return 32; }
    @Override public short nSk()     { return 32; }
    @Override public short kemId()   { return 0x0020; }

    private static byte[] serializePublicKey(XECPublicKey publicKey) {
        return KeyUtils.toRawX25519PublicKeyBytes(publicKey);
    }

    private static XECPublicKey deserializePublicKey(byte[] enc) {
        return KeyUtils.fromRawX25519PublicKey(enc);
    }

    /**
     * Section 4.1 DH-Based KEM (DHKEM):
     *
     * <pre>
     * def ExtractAndExpand(dh, kem_context):
     *   eae_prk = LabeledExtract("", "eae_prk", dh)
     *   shared_secret = LabeledExpand(eae_prk, "shared_secret",
     *                                 kem_context, Nsecret)
     *   return shared_secret
     * </pre>
     */
    private byte[] extractAndExpand(byte[] dh, byte[] kemContext) {
        byte[] eaePrk = labeledExtractForSuite(HKDF, DHKEM_SUITE_ID_LABEL, EMPTY_LABEL, EAE_PRK_LABEL, dh);
        return labeledExpandForSuite(HKDF, eaePrk, DHKEM_SUITE_ID_LABEL, SHARED_SECRET_LABEL, kemContext, nSecret());
    }

    /**
     * Section 4.1 DH-Based KEM (DHKEM):
     *
     * <pre>
     * def Encap(pkR):
     *   skE, pkE = GenerateKeyPair()
     *   dh = DH(skE, pkR)
     *   enc = SerializePublicKey(pkE)
     *
     *   pkRm = SerializePublicKey(pkR)
     *   kem_context = concat(enc, pkRm)
     *
     *   shared_secret = ExtractAndExpand(dh, kem_context)
     *   return shared_secret, enc
     * </pre>
     */
    @Override
    public EncapResult encap(XECPublicKey pkR) {
        var kpE = keyPairGen.get();
        var skE = (XECPrivateKey)kpE.getPrivate();
        var pkE = (XECPublicKey)kpE.getPublic();

        byte[] dh = KeyUtils.ecdh(skE, pkR);
        byte[] enc = serializePublicKey(pkE);

        byte[] pkRm = serializePublicKey(pkR);
        byte[] kemContext = concat(enc, pkRm);

        byte[] sharedSecret = extractAndExpand(dh, kemContext);
        return new EncapResult(sharedSecret, enc);
    }

    /**
     * Section 4.1 DH-Based KEM (DHKEM):
     *
     * <pre>
     * def AuthEncap(pkR, skS):
     *   skE, pkE = GenerateKeyPair()
     *   dh = concat(DH(skE, pkR), DH(skS, pkR))
     *   enc = SerializePublicKey(pkE)
     *
     *   pkRm = SerializePublicKey(pkR)
     *   pkSm = SerializePublicKey(pk(skS))
     *   kem_context = concat(enc, pkRm, pkSm)
     *
     *   shared_secret = ExtractAndExpand(dh, kem_context)
     *   return shared_secret, enc
     * </pre>
     */
    @Override
    public EncapResult authEncap(XECPublicKey pkR, XECPrivateKey skS) {
        var kpE = keyPairGen.get();
        var skE = (XECPrivateKey)kpE.getPrivate();
        var pkE = (XECPublicKey)kpE.getPublic();

        byte[] dh = concat(KeyUtils.ecdh(skE, pkR), KeyUtils.ecdh(skS, pkR));
        byte[] enc = serializePublicKey(pkE);

        byte[] pkRm = serializePublicKey(pkR);
        byte[] pkSm = serializePublicKey(KeyUtils.extractX25519PublicKey(skS));
        byte[] kemContext = concat(enc, pkRm, pkSm);

        byte[] sharedSecret = extractAndExpand(dh, kemContext);
        return new EncapResult(sharedSecret, enc);
    }

    /**
     * Section 4.1 DH-Based KEM (DHKEM):
     *
     * <pre>
     * def Decap(enc, skR):
     *   pkE = DeserializePublicKey(enc)
     *   dh = DH(skR, pkE)
     *
     *   pkRm = SerializePublicKey(pk(skR))
     *   kem_context = concat(enc, pkRm)
     *
     *   shared_secret = ExtractAndExpand(dh, kem_context)
     *   return shared_secret
     * </pre>
     */
    @Override
    public byte[] decap(byte[] enc, XECPrivateKey skR) {
        var pkE = deserializePublicKey(enc);
        byte[] dh = KeyUtils.ecdh(skR, pkE);

        byte[] pkRm = serializePublicKey(KeyUtils.extractX25519PublicKey(skR));
        byte[] kemContext = concat(enc, pkRm);

        return extractAndExpand(dh, kemContext);
    }

    /**
     * Section 4.1 DH-Based KEM (DHKEM):
     *
     * <pre>
     * def AuthDecap(enc, skR, pkS):
     *   pkE = DeserializePublicKey(enc)
     *   dh = concat(DH(skR, pkE), DH(skR, pkS))
     *
     *   pkRm = SerializePublicKey(pk(skR))
     *   pkSm = SerializePublicKey(pkS)
     *   kem_context = concat(enc, pkRm, pkSm)
     *
     *   shared_secret = ExtractAndExpand(dh, kem_context)
     *   return shared_secret
     * </pre>
     */
    public byte[] authDecap(byte[] enc, XECPrivateKey skR, XECPublicKey pkS) {
        var pkE = deserializePublicKey(enc);
        byte[] dh = concat(KeyUtils.ecdh(skR, pkE), KeyUtils.ecdh(skR, pkS));

        byte[] pkRm = serializePublicKey(KeyUtils.extractX25519PublicKey(skR));
        byte[] pkSm = serializePublicKey(pkS);
        byte[] kemContext = concat(enc, pkRm, pkSm);

        return extractAndExpand(dh, kemContext);
    }

}
