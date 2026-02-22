// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
enum {
    FRTE_NO_ERROR = 0,
    FRTE_RPC_FIRST = 100,
    FRTE_RPC_GENERAL_ERROR = 100,
    FRTE_RPC_NOT_IMPLEMENTED = 101,
    FRTE_RPC_ABORT = 102,
    FRTE_RPC_TIMEOUT = 103,
    FRTE_RPC_CONNECTION = 104,
    FRTE_RPC_BAD_REQUEST = 105,
    FRTE_RPC_NO_SUCH_METHOD = 106,
    FRTE_RPC_WRONG_PARAMS = 107,
    FRTE_RPC_OVERLOAD = 108,
    FRTE_RPC_WRONG_RETURN = 109,
    FRTE_RPC_BAD_REPLY = 110,
    FRTE_RPC_METHOD_FAILED = 111,
    FRTE_RPC_PERMISSION_DENIED = 112,
    FRTE_RPC_LAST = 199
};

const char* FRT_GetErrorCodeName(uint32_t errorCode);
const char* FRT_GetDefaultErrorMessage(uint32_t errorCode);
