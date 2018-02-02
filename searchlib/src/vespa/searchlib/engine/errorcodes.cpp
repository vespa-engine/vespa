// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "errorcodes.h"

namespace search::engine {

const char *
getStringFromErrorCode(ErrorCode ecode)
{
    switch (ecode) {
    case ECODE_NO_ERROR:
        return "No error has occurred";
    case ECODE_GENERAL_ERROR:
        return "General error";
    case ECODE_QUERY_PARSE_ERROR:
        return "Error parsing query";
    case ECODE_ALL_PARTITIONS_DOWN:
        return "All searchnodes are down. This might indicate that no index is available yet.";
    case ECODE_ILLEGAL_DATASET:
        return "No such dataset";
    case ECODE_OVERLOADED:
        return "System is overloaded";
    case ECODE_NOT_IMPLEMENTED:
        return "The requested functionality is not implemented";
    case ECODE_QUERY_NOT_ALLOWED:
        return "Query not allowed to run";
    case ECODE_TIMEOUT:
        return "Query timed out";
    }
    return "Unknown error";
}

}

