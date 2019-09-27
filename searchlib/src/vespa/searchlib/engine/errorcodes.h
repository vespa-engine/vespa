// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::engine {

/**
 * Enum defining global error codes.
 * Used in error_code field in search::fs4transport::PCODE_ERROR packets.
 **/
enum ErrorCode {
    ECODE_QUERY_PARSE_ERROR   = 2
};

}

