// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/common.h>

namespace mbus {

/**
 * This class contains the reserved error codes that are used by
 * messagebus. It also defines the error code ranges that may be used
 * by applications. Note that this class should never be instantiated.
 * An error code is a number with some added semantics. Legal error
 * codes are separated into 4 value ranges. An error can be either
 * fatal or transient. Inside each category, the error can be either
 * messagebus specific or application specific. A fatal error signals
 * that something is wrong and that it will not help to resend the
 * message. A transient error signals that it may help to resend the
 * message at a later time.
 * <pre>
 * transient errors:
 *     messagebus:  [100000, 150000>
 *     application: [150000, 200000>
 * fatal errors:
 *     messagebus:  [200000, 250000>
 *     application: [250000, 300000>
 * </pre>
 **/
class ErrorCode {
public:
    enum {
        // The code is here for completeness.
        NONE = 0,

        // A general transient error, resending is possible.
        TRANSIENT_ERROR = 100000,

        // Sending was rejected because throttler capacity is full.
        SEND_QUEUE_FULL = TRANSIENT_ERROR + 1,

        // No addresses found for the services of the message route.
        NO_ADDRESS_FOR_SERVICE = TRANSIENT_ERROR + 2,

        // A connection problem occured while sending.
        CONNECTION_ERROR = TRANSIENT_ERROR + 3,

        // The session specified for the message is unknown.
        UNKNOWN_SESSION = TRANSIENT_ERROR + 4,

        // The recipient session is busy.
        SESSION_BUSY = TRANSIENT_ERROR + 5,

        // Sending aborted by route verification.
        SEND_ABORTED = TRANSIENT_ERROR + 6,

        // Version handshake failed for any reason.
        HANDSHAKE_FAILED = TRANSIENT_ERROR + 7,

        // An application specific transient error.
        APP_TRANSIENT_ERROR = TRANSIENT_ERROR + 50000,

        // A general non-recoverable error, resending is not possible.
        FATAL_ERROR = 200000,

        // Sending was rejected because throttler is closed.
        SEND_QUEUE_CLOSED = FATAL_ERROR + 1,

        // The route of the message is illegal.
        ILLEGAL_ROUTE = FATAL_ERROR + 2,

        // No services found for the message route.
        NO_SERVICES_FOR_ROUTE = FATAL_ERROR + 3,

        // The selected service was out of service.
        // Unused.....
        // SERVICE_OOS = FATAL_ERROR + 4,

        // An error occured while encoding the message.
        ENCODE_ERROR = FATAL_ERROR + 5,

        // A fatal network error occured while sending.
        NETWORK_ERROR = FATAL_ERROR + 6,

        // The protocol specified for the message is unknown.
        UNKNOWN_PROTOCOL = FATAL_ERROR + 7,

        // An error occured while decoding the message.
        DECODE_ERROR = FATAL_ERROR + 8,

        // A timeout occured while sending.
        TIMEOUT = FATAL_ERROR + 9,

        // The target is running an incompatible version.
        INCOMPATIBLE_VERSION = FATAL_ERROR + 10,

        // The policy specified in a route is unknown.
        UNKNOWN_POLICY = FATAL_ERROR + 11,

        // The network was shut down when attempting to send.
        NETWORK_SHUTDOWN = FATAL_ERROR + 12,

        // Exception thrown by routing policy.
        POLICY_ERROR = FATAL_ERROR + 13,

        // Exception thrown by routing policy.
        SEQUENCE_ERROR = FATAL_ERROR + 14,

        // An application specific non-recoverable error.
        APP_FATAL_ERROR = FATAL_ERROR + 50000,

        // No error codes are allowed to be this big.
        ERROR_LIMIT = APP_FATAL_ERROR + 50000
    };

    /**
     * Translates the given error code into its symbolic name.
     *
     * @param errorCode The error code to translate.
     * @return The symbolic name.
     **/
    static string getName(uint32_t errorCode);

private:
    ErrorCode(); // hide
};

} // namespace mbus

