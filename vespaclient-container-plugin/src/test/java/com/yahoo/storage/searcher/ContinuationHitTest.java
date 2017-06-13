// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.document.BucketId;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorIterator;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

public class ContinuationHitTest {

    private static final String SINGLE_BUCKET_URL_SAFE_BASE64
            = "AAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAHqNFZ4mrz-_wAAAAAAAAAA";
    private static final String MULTI_BUCKET_URL_SAFE_BASE64
            = "AAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwAAAAPqNFZ4mrz--gAAAAAAAAAA6" +
              "jRWeJq8_vsAAAAAAAAAAOo0VniavP7_AAAAAAAAAAA=";

    @Test
    public void continuationTokensAreUrlSafeBase64Encoded() throws Exception {
        ContinuationHit hit = new ContinuationHit(createSingleBucketProgress());
        // We want -_ instead of +/
        assertEquals(SINGLE_BUCKET_URL_SAFE_BASE64, hit.getValue());
    }

    @Test
    public void continuationTokensAreNotBrokenIntoMultipleLines() throws Exception {
        ContinuationHit hit = new ContinuationHit(createMultiBucketProgress());
        assertTrue(hit.getValue().length() > 76); // Ensure we exceed MIME line length limits.
        assertFalse(hit.getValue().contains("\n"));
    }

    @Test
    public void decodingAcceptsUrlSafeTokens() throws Exception {
        final ProgressToken token = ContinuationHit.getToken(SINGLE_BUCKET_URL_SAFE_BASE64);
        // Roundtrip should yield identical results.
        assertEquals(SINGLE_BUCKET_URL_SAFE_BASE64,
                     new ContinuationHit(token).getValue());
    }

    /**
     * Legacy Base64 encoder emitted MIME Base64. Ensure we handle tokens from that era.
     */
    @Test
    public void decodingAcceptsLegacyNonUrlSafeTokens() throws Exception {
        final String legacyBase64 = convertedToMimeBase64Chars(SINGLE_BUCKET_URL_SAFE_BASE64);
        final ProgressToken legacyToken = ContinuationHit.getToken(legacyBase64);

        assertEquals(SINGLE_BUCKET_URL_SAFE_BASE64,
                     new ContinuationHit(legacyToken).getValue());
    }

    /**
     * Legacy Base64 encoder would happily output line breaks after each MIME line
     * boundary. Ensure we handle these gracefully.
     */
    @Test
    public void decodingAcceptsLegacyMimeLineBrokenTokens() throws Exception {
        final String multiBucketLegacyToken =
                "AAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwAAAAPqNFZ4mrz++gAAAAAAAAAA6jRWeJq8/vsA\r\n" +
                "AAAAAAAAAOo0VniavP7/AAAAAAAAAAA=";
        final ProgressToken legacyToken = ContinuationHit.getToken(multiBucketLegacyToken);

        assertEquals(MULTI_BUCKET_URL_SAFE_BASE64,
                     new ContinuationHit(legacyToken).getValue());
    }

    /**
     * Returns a ProgressToken whose base 64 representation will be _less_ than 76 bytes (MIME line limit)
     */
    private ProgressToken createSingleBucketProgress() {
        ProgressToken token = new ProgressToken(16);
        // Use explicit bucket set so we can better control the binary representation
        // of the buckets, and thus the values written as base 64.
        Set<BucketId> buckets = new TreeSet<>();
        // This particular bucket ID will contain +/ chars when output as non-URL safe base 64.
        buckets.add(new BucketId(58, 0x123456789abcfeffL));
        VisitorIterator.createFromExplicitBucketSet(buckets, 16, token); // "Prime" the token.
        return token;
    }

    /**
     * Returns a ProgressToken whose base 64 representation will be _more_ than 76 bytes (MIME line limit)
     */
    private ProgressToken createMultiBucketProgress() {
        ProgressToken token = new ProgressToken(16);
        Set<BucketId> buckets = new TreeSet<>();
        buckets.add(new BucketId(58, 0x123456789abcfeffL));
        buckets.add(new BucketId(58, 0x123456789abcfefaL));
        buckets.add(new BucketId(58, 0x123456789abcfefbL));
        VisitorIterator.createFromExplicitBucketSet(buckets, 16, token); // "Prime" the token.
        return token;
    }

    private String convertedToMimeBase64Chars(String token) {
        // Doesn't split on MIME line boundaries, so not fully MIME compliant.
        return token.replace('-', '+').replace('_', '/');
    }

}
