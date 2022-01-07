// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import com.yahoo.text.Utf8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Convenience class for hashing a String with MD5, and either returning
 * an int with the 4 LSBytes, or the whole 12-byte MD5 hash.
 * <p>
 * Note that instantiating this class can be expensive, so re-using instances
 * is a good idea.
 * <p>
 * This class is not thread safe.
 *
 * @author Einar M R Rosenvinge
 */
public class MD5 {

    public static final ThreadLocal<MessageDigest> md5 = new MD5Factory();

    private static class MD5Factory extends ThreadLocal<MessageDigest> {

        @Override
        protected MessageDigest initialValue() {
            return createMD5();
        }
    }

    private static MessageDigest createMD5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    final private MessageDigest digester;

    public MD5() {
        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("MD5 algorithm not found.");
        }
    }

    public int hash(String s) {
        byte[] md5 = digester.digest(Utf8.toBytes(s));
        int hash = 0;
        assert (md5.length == 16);

        //produce an int by using only the 32 lsb:
        int byte1 = (((int) md5[12]) << 24) & 0xFF000000;
        int byte2 = (((int) md5[13]) << 16) & 0x00FF0000;
        int byte3 = (((int) md5[14]) << 8) & 0x0000FF00;
        int byte4 = (((int) md5[15])) & 0x000000FF;

        hash |= byte1;
        hash |= byte2;
        hash |= byte3;
        hash |= byte4;
        return hash;
    }

    public byte[] hashFull(String s) {
        return digester.digest(Utf8.toBytes(s));
    }

}
