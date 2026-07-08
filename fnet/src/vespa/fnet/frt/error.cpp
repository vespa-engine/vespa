// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "error.h"

const char* FRT_GetErrorCodeName(uint32_t errorCode) {
    if (errorCode == 0)
        return "FRTE_NO_ERROR";
    if (errorCode > 0xffff)
        return "[APPLICATION ERROR]";

    if (errorCode >= FRTE_RPC_FIRST && errorCode <= FRTE_RPC_LAST) {
        switch (errorCode) {
        case FRTE_RPC_GENERAL_ERROR:
            return "FRTE_RPC_GENERAL_ERROR";
        case FRTE_RPC_NOT_IMPLEMENTED:
            return "FRTE_RPC_NOT_IMPLEMENTED";
        case FRTE_RPC_ABORT:
            return "FRTE_RPC_ABORT";
        case FRTE_RPC_TIMEOUT:
            return "FRTE_RPC_TIMEOUT";
        case FRTE_RPC_CONNECTION:
            return "FRTE_RPC_CONNECTION";
        case FRTE_RPC_BAD_REQUEST:
            return "FRTE_RPC_BAD_REQUEST";
        case FRTE_RPC_NO_SUCH_METHOD:
            return "FRTE_RPC_NO_SUCH_METHOD";
        case FRTE_RPC_WRONG_PARAMS:
            return "FRTE_RPC_WRONG_PARAMS";
        case FRTE_RPC_OVERLOAD:
            return "FRTE_RPC_OVERLOAD";
        case FRTE_RPC_WRONG_RETURN:
            return "FRTE_RPC_WRONG_RETURN";
        case FRTE_RPC_BAD_REPLY:
            return "FRTE_RPC_BAD_REPLY";
        case FRTE_RPC_METHOD_FAILED:
            return "FRTE_RPC_METHOD_FAILED";
        case FRTE_RPC_PERMISSION_DENIED:
            return "FRTE_RPC_PERMISSION_DENIED";
        default:
            return "[UNKNOWN RPC ERROR]";
        }
    }
    return "[UNKNOWN ERROR]";
}

const char* FRT_GetDefaultErrorMessage(uint32_t errorCode) {
    if (errorCode == 0)
        return "No error";
    if (errorCode > 0xffff)
        return "[APPLICATION ERROR]";

    if (errorCode >= FRTE_RPC_FIRST && errorCode <= FRTE_RPC_LAST) {
        switch (errorCode) {
        case FRTE_RPC_GENERAL_ERROR:
            return "(RPC) General error";
        case FRTE_RPC_NOT_IMPLEMENTED:
            return "(RPC) Not implemented";
        case FRTE_RPC_ABORT:
            return "(RPC) Invocation aborted";
        case FRTE_RPC_TIMEOUT:
            return "(RPC) Invocation timed out";
        case FRTE_RPC_CONNECTION:
            return "(RPC) Connection error";
        case FRTE_RPC_BAD_REQUEST:
            return "(RPC) Bad request packet";
        case FRTE_RPC_NO_SUCH_METHOD:
            return "(RPC) No such method";
        case FRTE_RPC_WRONG_PARAMS:
            return "(RPC) Illegal parameters";
        case FRTE_RPC_OVERLOAD:
            return "(RPC) Request dropped due to server overload";
        case FRTE_RPC_WRONG_RETURN:
            return "(RPC) Illegal return values";
        case FRTE_RPC_BAD_REPLY:
            return "(RPC) Bad reply packet";
        case FRTE_RPC_METHOD_FAILED:
            return "(RPC) Method failed";
        case FRTE_RPC_PERMISSION_DENIED:
            return "(RPC) Permission denied";
        default:
            return "[UNKNOWN RPC ERROR]";
        }
    }
    return "[UNKNOWN ERROR]";
}
