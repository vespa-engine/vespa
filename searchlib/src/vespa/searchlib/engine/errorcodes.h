// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::engine {

/**
 * Enum defining global error codes.
 * Used in error_code field in search::fs4transport::PCODE_ERROR packets.
 **/
enum ErrorCode {
    ECODE_NO_ERROR            = 0,
    ECODE_GENERAL_ERROR       = 1,
    ECODE_QUERY_PARSE_ERROR   = 2,
    ECODE_ALL_PARTITIONS_DOWN = 3,
    ECODE_ILLEGAL_DATASET     = 4,
    ECODE_OVERLOADED          = 5,
    ECODE_NOT_IMPLEMENTED     = 6,
    ECODE_QUERY_NOT_ALLOWED   = 7,
    ECODE_TIMEOUT             = 8
};

/**
 * Normally error codes should be accompanied by an error message
 * describing the error. If no such message is present, this method
 * may be used to obtain the default description of an error code.
 *
 * @param error the error code we want info about.
 * @return the default error message for the given error code.
 **/
const char* getStringFromErrorCode(ErrorCode error);

}

