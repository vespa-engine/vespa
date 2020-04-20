// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class ErrorTypeTest {

    @Test
    public void testErrorType() {
        assertThat(ErrorType.getErrorType(com.yahoo.jrt.ErrorCode.CONNECTION), is(ErrorType.TRANSIENT));
        assertThat(ErrorType.getErrorType(com.yahoo.jrt.ErrorCode.TIMEOUT), is(ErrorType.TRANSIENT));
        assertThat(ErrorType.getErrorType(ErrorCode.UNKNOWN_CONFIG), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.UNKNOWN_DEFINITION), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.UNKNOWN_DEF_MD5), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_NAME), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_VERSION), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_CONFIGID), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_DEF_MD5), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_CONFIG_MD5), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_TIMEOUT), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_GENERATION), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_SUB_FLAG), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.OUTDATED_CONFIG), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.INTERNAL_ERROR), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(ErrorCode.ILLEGAL_SUB_FLAG), is(ErrorType.FATAL));
        assertThat(ErrorType.getErrorType(0xdeadc0de), is(ErrorType.FATAL));
    }

}
