// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

import java.security.KeyPair;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.util.Arrays;

import static com.yahoo.security.ArrayUtils.concat;
import static com.yahoo.security.hpke.Constants.BASE_NONCE_LABEL;
import static com.yahoo.security.hpke.Constants.DEFAULT_PSK;
import static com.yahoo.security.hpke.Constants.DEFAULT_PSK_ID;
import static com.yahoo.security.hpke.Constants.EMPTY_LABEL;
import static com.yahoo.security.hpke.Constants.EXP_LABEL;
import static com.yahoo.security.hpke.Constants.INFO_HASH_LABEL;
import static com.yahoo.security.hpke.Constants.KEY_LABEL;
import static com.yahoo.security.hpke.Constants.PSK_ID_HASH_LABEL;
import static com.yahoo.security.hpke.Constants.SECRET_LABEL;
import static com.yahoo.security.hpke.LabeledKdfUtils.i2osp2;
import static com.yahoo.security.hpke.LabeledKdfUtils.labeledExpandForSuite;
import static com.yahoo.security.hpke.LabeledKdfUtils.labeledExtractForSuite;

/**
 * Restricted subset implementation of RFC 9180 Hybrid Public Key Encryption (HPKE)
 * <p>
 * HPKE is an encryption scheme that builds around three primitives:
 * </p>
 * <ul>
 *     <li>A key encapsulation mechanism (KEM)</li>
 *     <li>A key derivation function (KDF)</li>
 *     <li>An "authenticated encryption with associated data" (AEAD) algorithm</li>
 * </ul>
 * <p>
 * The 3-tuple (KEM, KDF, AEAD) is known as the HPKE <em>ciphersuite</em>.
 * </p>
 * <p>
 * This implementation has certain (intentional) limitations:
 * </p>
 * <ul>
 *     <li>Only the <code>DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-128-GCM</code> ciphersuite is
 *         implemented. This is expected to be a good default choice for any internal use of this class.</li>
 *     <li>Only the "base mode" (unauthenticated sender) is supported, i.e. no PSK support and no
 *         secret exporting. This implementation is only expected to be used for anonymous one-way
 *         encryption.</li>
 *     <li>The API only offers single-shot encryption to keep anyone from being tempted to
 *         use it to build their own multi-message protocol on top. This entirely avoids the
 *         risk of nonce reuse caused by accidentally repeating sequence numbers.</li>
 * </ul>
 * <p>
 * <em>Deprecation notice:</em> once BouncyCastle (or the Java crypto API) supports HPKE, this
 * particular implementation can safely be deprecated and sent off to live on a farm.
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9180.html">RFC 9180 Hybrid Public Key Encryption</a>
 *
 * @author vekterli
 *
 */
public final class Hpke {

    private final Kem kem;
    private final Kdf kdf;
    private final Aead aead;
    private final byte[] hpkeSuiteId;

    private Hpke(Ciphersuite ciphersuite) {
        this.kem  = ciphersuite.kem();
        this.kdf  = ciphersuite.kdf();
        this.aead = ciphersuite.aead();
        this.hpkeSuiteId = makeHpkeSuiteId();
    }

    public static Hpke of(Ciphersuite ciphersuite) {
        return new Hpke(ciphersuite);
    }

    /**
     * Section 5.1 Creating the Encryption Context:
     *
     * HPKE implicit suite_id (this differs from the KEM suite id):
     *
     * <pre>
     * suite_id = concat(
     *   "HPKE",
     *   I2OSP(kem_id, 2),
     *   I2OSP(kdf_id, 2),
     *   I2OSP(aead_id, 2)
     * )
     * </pre>
     */
    private byte[] makeHpkeSuiteId() {
        byte[] hpkePrefix = new byte[] { 'H','P','K','E' };
        return concat(hpkePrefix, i2osp2(kem.kemId()), i2osp2(kdf.kdfId()), i2osp2(aead.aeadId()));
    }

    byte[] labeledExtractHpke(byte[] salt, byte[] label, byte[] ikm) {
        return labeledExtractForSuite(kdf, hpkeSuiteId, salt, label, ikm);
    }

    byte[] labeledExpandHpke(byte[] prk, byte[] label, byte[] info, int nBytesToExpand/*L*/) {
        return labeledExpandForSuite(kdf, prk, hpkeSuiteId, label, info, nBytesToExpand);
    }

    /*
     * HPKE supports several modes, where all but the first one are sender-authenticated:
     *
     *  Mode           Value
     *  mode_base      0x00
     *  mode_psk       0x01
     *  mode_auth      0x02
     *  mode_auth_psk  0x03
     *
     * We only support mode_base, as our primary use case is encryption where the sender is
     * not authenticated.
     */
    private static final byte MODE_BASE     = 0x00;
    private static final byte MODE_PSK      = 0x01;
    private static final byte MODE_AUTH     = 0x02;
    private static final byte MODE_AUTH_PSK = 0x03;

    /**
     * Section 5.1 Creating the Encryption Context:
     *
     * <pre>
     * def VerifyPSKInputs(mode, psk, psk_id):
     *   got_psk = (psk != default_psk)
     *   got_psk_id = (psk_id != default_psk_id)
     *   if got_psk != got_psk_id:
     *     raise Exception("Inconsistent PSK inputs")
     *
     *   if got_psk and (mode in [mode_base, mode_auth]):
     *     raise Exception("PSK input provided when not needed")
     *   if (not got_psk) and (mode in [mode_psk, mode_auth_psk]):
     *     raise Exception("Missing required PSK input")
     * </pre>
     *
     * Even though we don't support PSK, we implement this method fully for the sake of conformance.
     */
    static void verifyPskInputs(byte mode, byte[] psk, byte[] pskId) {
        boolean gotPsk   = !Arrays.equals(psk, DEFAULT_PSK);
        boolean gotPskId = !Arrays.equals(pskId, DEFAULT_PSK_ID);
        if (gotPsk != gotPskId) {
            throw new IllegalArgumentException("Inconsistent PSK inputs");
        }
        if (gotPsk && (mode == MODE_BASE || mode == MODE_AUTH)) {
            throw new IllegalArgumentException("PSK input provided when not needed");
        }
        if (!gotPsk && (mode == MODE_PSK || mode == MODE_AUTH_PSK)) {
            throw new IllegalArgumentException("Missing required PSK input");
        }
    }

    /**
     * Section 7.2.1 Input Length Restrictions:
     *
     * "The RECOMMENDED limit for these values(*) is 64 bytes. This would enable interoperability
     *  with implementations that statically allocate memory for these inputs to avoid memory allocations."
     *
     * (*) psk, pskId, info in our use case
     */
    private static final int MAX_INPUT_LENGTH = 64;

    static void verifyInputLengthRestrictions(byte[] psk, byte[] pskId, byte[] info) {
        if (psk.length > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Input PSK length (%d) greater than max length (%d)"
                                               .formatted(psk.length, MAX_INPUT_LENGTH));
        }
        if (pskId.length > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Input PSK ID length (%d) greater than max length (%d)"
                                               .formatted(pskId.length, MAX_INPUT_LENGTH));
        }
        if (info.length > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Input info length (%d) greater than max length (%d)"
                                               .formatted(info.length, MAX_INPUT_LENGTH));
        }
    }

    private record ContextBase(byte[] key, byte[] nonce, long seqNum, byte[] exporterSecret) { }

    /**
     * Section 5.1 Creating the Encryption Context:
     *
     * <pre>
     * def KeySchedule&lt;ROLE&gt;(mode, shared_secret, info, psk, psk_id):
     *   VerifyPSKInputs(mode, psk, psk_id)
     *
     *   psk_id_hash = LabeledExtract("", "psk_id_hash", psk_id)
     *   info_hash = LabeledExtract("", "info_hash", info)
     *   key_schedule_context = concat(mode, psk_id_hash, info_hash)
     *
     *   secret = LabeledExtract(shared_secret, "secret", psk)
     *
     *   key = LabeledExpand(secret, "key", key_schedule_context, Nk)
     *   base_nonce = LabeledExpand(secret, "base_nonce",
     *                              key_schedule_context, Nn)
     *   exporter_secret = LabeledExpand(secret, "exp",
     *                                   key_schedule_context, Nh)
     *
     *   return Context&lt;ROLE&gt;(key, base_nonce, 0, exporter_secret)
     * </pre>
     *
     * Note: Labeled*-functions above implicitly include the HPKE suite_id. We do it explicitly.
     * We also throw in an input length check as recommended in Section 7.2.1.
     */
    ContextBase keySchedule(byte mode, byte[] sharedSecret, byte[] info, byte[] psk, byte[] pskId) {
        verifyPskInputs(mode, psk, pskId);
        verifyInputLengthRestrictions(psk, pskId, info);

        byte[] pskIdHash = labeledExtractHpke(EMPTY_LABEL, PSK_ID_HASH_LABEL, pskId); // Kdf.nH() bytes returned
        byte[] infoHash  = labeledExtractHpke(EMPTY_LABEL, INFO_HASH_LABEL,   info );
        byte[] keyScheduleContext = concat(new byte[]{mode}, pskIdHash, infoHash);

        byte[] secret = labeledExtractHpke(sharedSecret, SECRET_LABEL, psk);

        byte[] key            = labeledExpandHpke(secret, KEY_LABEL,        keyScheduleContext, aead.nK());
        byte[] baseNonce      = labeledExpandHpke(secret, BASE_NONCE_LABEL, keyScheduleContext, aead.nN());
        byte[] exporterSecret = labeledExpandHpke(secret, EXP_LABEL,        keyScheduleContext, kdf.nH());

        return new ContextBase(key, baseNonce, 0, exporterSecret);
    }

    private record ContextS(byte[] enc, ContextBase base) {}
    private record ContextR(ContextBase base) {}

    /**
     * Section 5.1.1 Encryption to a Public Key:
     *
     * <pre>
     * def SetupBaseS(pkR, info):
     *   shared_secret, enc = Encap(pkR)
     *   return enc, KeyScheduleS(mode_base, shared_secret, info,
     *                            default_psk, default_psk_id)
     * </pre>
     */
    ContextS setupBaseS(XECPublicKey pkR, byte[] info) {
        var encapped = kem.encap(pkR);
        return new ContextS(encapped.enc(),
                            keySchedule(MODE_BASE, encapped.sharedSecret(), info, DEFAULT_PSK, DEFAULT_PSK_ID));
    }

    /**
     * Section 5.1.1 Encryption to a Public Key:
     *
     * <pre>
     * def SetupBaseR(enc, skR, info):
     *   shared_secret = Decap(enc, skR)
     *   return KeyScheduleR(mode_base, shared_secret, info,
     *                       default_psk, default_psk_id)
     * </pre>
     */
    ContextR setupBaseR(byte[] enc, XECPrivateKey skR, byte[] info) {
        byte[] sharedSecret = kem.decap(enc, skR);
        return new ContextR(keySchedule(MODE_BASE, sharedSecret, info, DEFAULT_PSK, DEFAULT_PSK_ID));
    }

    public record Sealed(byte[] enc, byte[] ciphertext) {}

    /**
     * Section 6.1 Encryption and Decryption:
     *
     * <pre>
     * def Seal&lt;MODE&gt;(pkR, info, aad, pt, ...):
     *   enc, ctx = Setup&lt;MODE&gt;S(pkR, info, ...)
     *   ct = ctx.Seal(aad, pt)
     *   return enc, ct
     * </pre>
     *
     * Section 5.2 Encryption and Decryption:
     *
     * Since we only support single-shot encryption we collapse ContextS.Seal into the
     * parent SealBASE, since we don't have to track sequence numbers. This means
     * ComputeNonce is a no-op since the first sequence number is 0 which will always
     * XOR to the same nonce.
     *
     * <pre>
     * def ContextS.Seal(aad, pt):
     *   ct = Seal(self.key, self.ComputeNonce(self.seq), aad, pt)
     *   self.IncrementSeq()
     *   return ct
     * </pre>
     */
    public Sealed sealBase(XECPublicKey pkR, byte[] info, byte[] aad, byte[] pt) {
        var encAndCtx = setupBaseS(pkR, info);
        var base = encAndCtx.base;
        byte[] ct = aead.seal(base.key(), base.nonce(), aad, pt);
        return new Sealed(encAndCtx.enc, ct);
    }

    /**
     * Section 6.1 Encryption and Decryption:
     *
     * <pre>
     * def Open&lt;MODE&gt;(enc, skR, info, aad, ct, ...):
     *   ctx = Setup&lt;MODE&gt;R(enc, skR, info, ...)
     *   return ctx.Open(aad, ct)
     * </pre>
     *
     * Section 5.2 Encryption and Decryption:
     *
     * Since we only support single-shot decryption we collapse ContextR.Open into the
     * parent OpenBASE, since we don't have to track sequence numbers. See also: sealBase()
     *
     * <pre>
     * def ContextR.Open(aad, ct):
     *   pt = Open(self.key, self.ComputeNonce(self.seq), aad, ct)
     *   if pt == OpenError:
     *     raise OpenError
     *   self.IncrementSeq()
     *   return pt
     * </pre>
     */
    public byte[] openBase(byte[] enc, XECPrivateKey skR, byte[] info, byte[] aad, byte[] ct) {
        var ctx = setupBaseR(enc, skR, info);
        var base = ctx.base;
        // TODO wrap any exceptions in OpenError et al?
        return aead.open(base.key(), base.nonce(), aad, ct);
    }

}
