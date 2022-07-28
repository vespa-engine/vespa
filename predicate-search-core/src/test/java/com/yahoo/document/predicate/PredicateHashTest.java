// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class PredicateHashTest{
    @Test
    void requireThatShortStringsGetsHashes() {
        assertHashesTo(0x82af3d1de65ec252L, "abcdefg");
        assertHashesTo(0xdc50d922fb0e91d6L, "雅虎");
        assertHashesTo(0x709bd6ff1a84dc14L, "country=日本");
        assertHashesTo(0x28e8de732ab0e809L, "foo");

        assertHashesTo(0x8db63936938575bfL, "");
        assertHashesTo(0x1d48fd74d88633acL, "a");
        assertHashesTo(0xd30019bef51f4a75L, "ab");
        assertHashesTo(0x9cb12e2bfea87243L, "abc");
        assertHashesTo(0x207e64432ec23f4bL, "abcd");
        assertHashesTo(0xbb1277971caf7a56L, "abcde");
        assertHashesTo(0xfde595baae539176L, "abcdef");
        assertHashesTo(0x82af3d1de65ec252L, "abcdefg");
        assertHashesTo(0x1cac1cd5db905a5fL, "abcdefgh");
        assertHashesTo(0x1ce1c26a201525deL, "abcdefghi");
        assertHashesTo(0x2237a417a20c1025L, "abcdefghij");
        assertHashesTo(0xd98f47421abc3754L, "abcdefghijk");
        assertHashesTo(0xb974917101764d3aL, "abcdefghijkl");
        assertHashesTo(0xde3b7ffe3e6dd61fL, "abcdefghijklm");
        assertHashesTo(0x31d95fa68634f482L, "abcdefghijklmn");
        assertHashesTo(0xde99d87fdbeca8faL, "abcdefghijklmn1");
        assertHashesTo(0x0afc8571f275c392L, "abcdefghijklmn12");
        assertHashesTo(0xbd00379443b0606cL, "abcdefghijklmn123");
        assertHashesTo(0x855c704c68e095c5L, "abcdefghijklmn1234");
        assertHashesTo(0xe9233cb6e4fad097L, "abcdefghijklmn12345");
        assertHashesTo(0x1103ca46bd6e8d2fL, "abcdefghijklmn123456");
        assertHashesTo(0x0c7097be717354d1L, "abcdefghijklmn1234567");
        assertHashesTo(0x3e75293210127583L, "abcdefghijklmn12345678");
        assertHashesTo(0xa66286e1294d8197L, "abcdefghijklmn123456789");
        assertHashesTo(0x79fac97d13f4cc84L, "abcdefghijklmn1234567890");
    }

    @Test
    void requireThatLongStringsGetsHashes() {
        assertHashesTo(0x79fac97d13f4cc84L, "abcdefghijklmn1234567890");
        assertHashesTo(0xd7af1798f1d5de44L, "abcdefghijklmn1234567890a");
        assertHashesTo(0x5a259ad887478cccL, "abcdefghijklmn1234567890ab");
        assertHashesTo(0x4e8d95bab8d64191L, "abcdefghijklmn1234567890abc");
        assertHashesTo(0xf63b94d31db2fe1aL, "abcdefghijklmn1234567890abcd");
        assertHashesTo(0x47a1977d65709aceL, "abcdefghijklmn1234567890abcde");
        assertHashesTo(0x52e1fb6d6aff3aeeL, "abcdefghijklmn1234567890abcdef");
        assertHashesTo(0xc16de639b6e69ad3L, "abcdefghijklmn1234567890abcdefg");
        assertHashesTo(0x87c22dd1e285dd6fL, "abcdefghijklmn1234567890abcdefgh");
        assertHashesTo(0x775a3542d88b4972L, "abcdefghijklmn1234567890abcdefghi");
        assertHashesTo(0x7b0c82116edf338bL, "abcdefghijklmn1234567890abcdefghij");
        assertHashesTo(0x0fe73b58f6b23cb6L, "abcdefghijklmn1234567890abcdefghijk");
        assertHashesTo(0x27ab8d02387e64e0L, "abcdefghijklmn1234567890abcdefghijkl");
        assertHashesTo(0xdd161af20b41be04L, "abcdefghijklmn1234567890abcdefghijklm");
        assertHashesTo(0x67739554f61fffcbL, "abcdefghijklmn1234567890abcdefghijklmn");
        assertHashesTo(0xa765cc6be247dfb2L, "abcdefghijklmn1234567890abcdefghijklmn1");
        assertHashesTo(0x9e201896cc600501L, "abcdefghijklmn1234567890abcdefghijklmn12");
        assertHashesTo(0xfc5077792bfed491L, "abcdefghijklmn1234567890abcdefghijklmn123");
        assertHashesTo(0x96a7acb73fd13601L, "abcdefghijklmn1234567890abcdefghijklmn1234");
        assertHashesTo(0x45de4237e48a0ba8L, "abcdefghijklmn1234567890abcdefghijklmn12345");
        assertHashesTo(0x3b65da96300e107eL, "abcdefghijklmn1234567890abcdefghijklmn123456");
        assertHashesTo(0xbd95c3591ee587bdL, "abcdefghijklmn1234567890abcdefghijklmn1234567");
        assertHashesTo(0x2688cb2d10e8629bL, "abcdefghijklmn1234567890abcdefghijklmn12345678");
        assertHashesTo(0xcd383d98f9483ef0L, "abcdefghijklmn1234567890abcdefghijklmn123456789");
        assertHashesTo(0x220e374268970e84L, "abcdefghijklmn1234567890abcdefghijklmn1234567890");
        assertHashesTo(0xd50ef002ed96bf0bL, "abcdefghijklmn1234567890abcdefghijklmn1234567890a");
        assertHashesTo(0x5ec9b42099bb25c6L, "abcdefghijklmn1234567890abcdefghijklmn1234567890ab");
        assertHashesTo(0x05c603997a19dbceL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abc");
        assertHashesTo(0xcee3fce2a3e38762L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcd");
        assertHashesTo(0xc0d9791b19897f0aL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcde");
        assertHashesTo(0xde98d0f8250ec703L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdef");
        assertHashesTo(0xa7688d5834fa7d2aL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefg");
        assertHashesTo(0xad514e8250667cdeL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefgh");
        assertHashesTo(0xf562662deca536c3L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghi");
        assertHashesTo(0x9d1b8d2463cde877L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghij");
        assertHashesTo(0x24840f21eeb30861L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijk");
        assertHashesTo(0x40af2a3f14d31fdaL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijkl");
        assertHashesTo(0x3514ad5e964b5c73L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklm");
        assertHashesTo(0x7bd6243490571844L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn");
        assertHashesTo(0x273de93a3bddd9e8L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn1");
        assertHashesTo(0x18e6850c3e2f85beL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn12");
        assertHashesTo(0x044968ddc534d822L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn123");
        assertHashesTo(0x7430d9d503fe624dL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn1234");
        assertHashesTo(0xf0bb1e5239c1d88cL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn12345");
        assertHashesTo(0x2ee1ab348b7deaa0L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn123456");
        assertHashesTo(0x18b6da5df76680dfL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn1234567");
        assertHashesTo(0x06c95ee4ddc93743L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn12345678");
        assertHashesTo(0x6406e477d8ca608dL, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn123456789");
        assertHashesTo(0x203397a04178d470L, "abcdefghijklmn1234567890abcdefghijklmn1234567890abcdefghijklmn1234567890");
    }

    private void assertHashesTo(long hash, String key) {
        assertEquals(hash, PredicateHash.hash64(key));
    }
}
