// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <string>

namespace slobrok {

/**
 * @struct OkState
 * @brief object representing the result status of a request
 *
 * Contains an error code (0 means success) and an error message string.
 **/
struct OkState
{
    const uint32_t    errorCode;
    const std::string errorMsg;

    OkState(uint32_t code, std::string msg) : errorCode(code), errorMsg(msg) {}
    OkState() : errorCode(0), errorMsg() {}
    bool ok() const { return errorCode == 0; }
    bool failed() const { return errorCode != 0; }
    enum SpecialErrorCodes {
        ALL_OK = 0,
        FAILED = 1,
        FAILED_BAD = 13
    };
};

} // namespace slobrok

