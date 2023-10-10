// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "errorcode.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace mbus {

ErrorCode::ErrorCode() { }

string
ErrorCode::getName(uint32_t errorCode)
{
    switch (errorCode) {
    case APP_FATAL_ERROR        : return "APP_FATAL_ERROR";
    case APP_TRANSIENT_ERROR    : return "APP_TRANSIENT_ERROR";
    case CONNECTION_ERROR       : return "CONNECTION_ERROR";
    case DECODE_ERROR           : return "DECODE_ERROR";
    case ENCODE_ERROR           : return "ENCODE_ERROR";
    case FATAL_ERROR            : return "FATAL_ERROR";
    case HANDSHAKE_FAILED       : return "HANDSHAKE_FAILED";
    case ILLEGAL_ROUTE          : return "ILLEGAL_ROUTE";
    case INCOMPATIBLE_VERSION   : return "INCOMPATIBLE_VERSION";
    case NETWORK_ERROR          : return "NETWORK_ERROR";
    case NETWORK_SHUTDOWN       : return "NETWORK_SHUTDOWN";
    case NO_ADDRESS_FOR_SERVICE : return "NO_ADDRESS_FOR_SERVICE";
    case NO_SERVICES_FOR_ROUTE  : return "NO_SERVICES_FOR_ROUTE";
    case NONE                   : return "NONE";
    case POLICY_ERROR           : return "POLICY_ERROR";
    case SEND_ABORTED           : return "SEND_ABORTED";
    case SEND_QUEUE_CLOSED      : return "SEND_QUEUE_CLOSED";
    case SEND_QUEUE_FULL        : return "SEND_QUEUE_FULL";
    case SEQUENCE_ERROR         : return "SEQUENCE_ERROR";
    case SESSION_BUSY           : return "SESSION_BUSY";
    case TIMEOUT                : return "TIMEOUT";
    case TRANSIENT_ERROR        : return "TRANSIENT_ERROR";
    case UNKNOWN_POLICY         : return "UNKNOWN_POLICY";
    case UNKNOWN_PROTOCOL       : return "UNKNOWN_PROTOCOL";
    case UNKNOWN_SESSION        : return "UNKNOWN_SESSION";
    default                     : {
            vespalib::asciistream os;
            os << "UNKNOWN(" << errorCode << ')';
            return os.str();
        }
    }
}

} // namespace mbus
