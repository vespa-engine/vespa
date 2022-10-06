// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Test for ensuring that the definitions of error codes in Java match their
 * C++ implementation counterparts. Any new DocumentProtocol error codes must
 * also be added to this test.
 *
 * @author vekterli
 */
public class ErrorCodesTest {

    private class NamedErrorCodes {
        private final TreeMap<String, Integer> nameAndCode = new TreeMap<>();

        public void put(String name, int code) {
            nameAndCode.put(name, code);
        }

        public String toSortedKeyValueString() {
            return nameAndCode.entrySet().stream()
                    .map((kv) -> kv.getKey() + " " + kv.getValue())
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * Always meant to be run against HEAD.
     */
    @Test
    public void errorCodesMatchCppDefinitions() throws Exception {
        final NamedErrorCodes codes = new NamedErrorCodes();
        enumerateAllDocumentProtocolErrorCodes(codes);

        final String javaGoldenFile = TestFileUtil.getPath("HEAD-java-golden-error-codes.txt");
        final String javaGoldenData = codes.toSortedKeyValueString();
        TestFileUtil.writeToFile(javaGoldenFile, javaGoldenData);

        final String cppGoldenFile = TestFileUtil.getPath("HEAD-cpp-golden-error-codes.txt");
        final String cppGoldenData = new String(TestFileUtil.readFile(cppGoldenFile), Charset.forName("UTF-8"));
        assertEquals(javaGoldenData, cppGoldenData);
    }

    /**
     * Emits all name to integral code value mappings for error codes that exist
     * in the Document protocol.
     *
     * This list must be updated (here and in the C++ equivalent) whenever a new
     * code is added, and the resulting file must be checked in.
     */
    private void enumerateAllDocumentProtocolErrorCodes(NamedErrorCodes codes) {
        codes.put("ERROR_MESSAGE_IGNORED", DocumentProtocol.ERROR_MESSAGE_IGNORED);
        codes.put("ERROR_POLICY_FAILURE", DocumentProtocol.ERROR_POLICY_FAILURE);
        codes.put("ERROR_DOCUMENT_NOT_FOUND", DocumentProtocol.ERROR_DOCUMENT_NOT_FOUND);
        codes.put("ERROR_DOCUMENT_EXISTS", DocumentProtocol.ERROR_DOCUMENT_EXISTS);
        codes.put("ERROR_REJECTED", DocumentProtocol.ERROR_REJECTED);
        codes.put("ERROR_NOT_IMPLEMENTED", DocumentProtocol.ERROR_NOT_IMPLEMENTED);
        codes.put("ERROR_ILLEGAL_PARAMETERS", DocumentProtocol.ERROR_ILLEGAL_PARAMETERS);
        codes.put("ERROR_UNKNOWN_COMMAND", DocumentProtocol.ERROR_UNKNOWN_COMMAND);
        codes.put("ERROR_NO_SPACE", DocumentProtocol.ERROR_NO_SPACE);
        codes.put("ERROR_IGNORED", DocumentProtocol.ERROR_IGNORED);
        codes.put("ERROR_INTERNAL_FAILURE", DocumentProtocol.ERROR_INTERNAL_FAILURE);
        codes.put("ERROR_TEST_AND_SET_CONDITION_FAILED", DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED);
        codes.put("ERROR_PROCESSING_FAILURE", DocumentProtocol.ERROR_PROCESSING_FAILURE);
        codes.put("ERROR_TIMESTAMP_EXIST", DocumentProtocol.ERROR_TIMESTAMP_EXIST); // (sic)
        codes.put("ERROR_NODE_NOT_READY", DocumentProtocol.ERROR_NODE_NOT_READY);
        codes.put("ERROR_WRONG_DISTRIBUTION", DocumentProtocol.ERROR_WRONG_DISTRIBUTION);
        codes.put("ERROR_ABORTED", DocumentProtocol.ERROR_ABORTED);
        codes.put("ERROR_BUSY", DocumentProtocol.ERROR_BUSY);
        codes.put("ERROR_NOT_CONNECTED", DocumentProtocol.ERROR_NOT_CONNECTED);
        codes.put("ERROR_DISK_FAILURE", DocumentProtocol.ERROR_DISK_FAILURE);
        codes.put("ERROR_IO_FAILURE", DocumentProtocol.ERROR_IO_FAILURE);
        codes.put("ERROR_BUCKET_NOT_FOUND", DocumentProtocol.ERROR_BUCKET_NOT_FOUND);
        codes.put("ERROR_BUCKET_DELETED", DocumentProtocol.ERROR_BUCKET_DELETED);
        codes.put("ERROR_STALE_TIMESTAMP", DocumentProtocol.ERROR_STALE_TIMESTAMP);
        codes.put("ERROR_SUSPENDED", DocumentProtocol.ERROR_SUSPENDED);
    }

    @Test
    public void getErrorNameIsDefinedForAllKnownProtocolErrorCodes() {
        final NamedErrorCodes codes = new NamedErrorCodes();
        enumerateAllDocumentProtocolErrorCodes(codes);
        codes.nameAndCode.entrySet().forEach(kv -> {
            // Error names are not prefixed by "ERROR_" unlike their enum counterparts.
            assertEquals(kv.getKey(), "ERROR_" + DocumentProtocol.getErrorName(kv.getValue()));
        });
    }
}
