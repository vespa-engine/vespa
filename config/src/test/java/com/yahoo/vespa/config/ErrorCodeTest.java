// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
/**
 * @author hmusum
 */
public class ErrorCodeTest {

    @Test
    public void basic() {
        assertThat(ErrorCode.getName(ErrorCode.INTERNAL_ERROR), is("INTERNAL_ERROR"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_CONFIG_MD5), is("ILLEGAL_CONFIG_MD5"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_CONFIGID), is("ILLEGAL_CONFIGID"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_DEF_MD5), is("ILLEGAL_DEF_MD5"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_GENERATION), is("ILLEGAL_GENERATION"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_NAME), is("ILLEGAL_NAME"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_SUB_FLAG), is("ILLEGAL_SUBSCRIBE_FLAG"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_TIMEOUT), is("ILLEGAL_TIMEOUT"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_VERSION), is("ILLEGAL_VERSION"));
        assertThat(ErrorCode.getName(ErrorCode.OUTDATED_CONFIG), is("OUTDATED_CONFIG"));
        assertThat(ErrorCode.getName(ErrorCode.UNKNOWN_CONFIG), is("UNKNOWN_CONFIG"));
        assertThat(ErrorCode.getName(ErrorCode.UNKNOWN_DEF_MD5), is("UNKNOWN_DEF_MD5"));
        assertThat(ErrorCode.getName(ErrorCode.UNKNOWN_DEFINITION), is("UNKNOWN_DEFINITION"));
        assertThat(ErrorCode.getName(ErrorCode.UNKNOWN_VESPA_VERSION), is("UNKNOWN_VESPA_VERSION"));
        assertThat(ErrorCode.getName(ErrorCode.INCONSISTENT_CONFIG_MD5), is("INCONSISTENT_CONFIG_MD5"));
        assertThat(ErrorCode.getName(ErrorCode.ILLEGAL_CLIENT_HOSTNAME), is("ILLEGAL_CLIENT_HOSTNAME"));

        assertThat(ErrorCode.getName(12345), is("Unknown error"));
    }
}
