// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * This interface contains the reserved error codes that are used for errors that occur within the messagebus.
 *
 * @author Simon Thoresen Hult
 */
public final class ErrorCode {

    /** The code is here for completeness. */
    public static final int NONE                   = 0;

    /** A general transient error, resending is possible. */
    public static final int TRANSIENT_ERROR        = 100000;

    /** Sending was rejected because throttler capacity is full. */
    public static final int SEND_QUEUE_FULL        = TRANSIENT_ERROR + 1;

    /** No addresses found for the services of the message route. */
    public static final int NO_ADDRESS_FOR_SERVICE = TRANSIENT_ERROR + 2;

    /** A connection problem occurred while sending. */
    public static final int CONNECTION_ERROR       = TRANSIENT_ERROR + 3;

    /** The session specified for the message is unknown. */
    public static final int UNKNOWN_SESSION        = TRANSIENT_ERROR + 4;

    /** The recipient session is busy. */
    public static final int SESSION_BUSY           = TRANSIENT_ERROR + 5;

    /** Sending aborted by route verification. */
    public static final int SEND_ABORTED           = TRANSIENT_ERROR + 6;

    /** Version handshake failed for any reason. */
    public static final int HANDSHAKE_FAILED       = TRANSIENT_ERROR + 7;

    /** An application specific transient error. */
    public static final int APP_TRANSIENT_ERROR    = TRANSIENT_ERROR + 50000;

    /** A general non-recoverable error, resending is not possible. */
    public static final int FATAL_ERROR            = 200000;

    /** Sending was rejected because throttler is closed. */
    public static final int SEND_QUEUE_CLOSED      = FATAL_ERROR + 1;

    /** The route of the message is illegal. */
    public static final int ILLEGAL_ROUTE          = FATAL_ERROR + 2;

    /** No services found for the message route. */
    public static final int NO_SERVICES_FOR_ROUTE  = FATAL_ERROR + 3;

    /** An error occurred while encoding the message. */
    public static final int ENCODE_ERROR           = FATAL_ERROR + 5;

    /** A fatal network error occurred while sending. */
    public static final int NETWORK_ERROR          = FATAL_ERROR + 6;

    /** The protocol specified for the message is unknown. */
    public static final int UNKNOWN_PROTOCOL       = FATAL_ERROR + 7;

    /** An error occurred while decoding the message. */
    public static final int DECODE_ERROR           = FATAL_ERROR + 8;

    /** A timeout occurred while sending. */
    public static final int TIMEOUT                = FATAL_ERROR + 9;

    /** The target is running an incompatible version. */
    public static final int INCOMPATIBLE_VERSION   = FATAL_ERROR + 10;

    /** The policy specified in a route is unknown. */
    public static final int UNKNOWN_POLICY         = FATAL_ERROR + 11;

    /** The network was shut down when attempting to send. */
    public static final int NETWORK_SHUTDOWN       = FATAL_ERROR + 12;

    /** Exception thrown by routing policy. */
    public static final int POLICY_ERROR           = FATAL_ERROR + 13;

    /** An error occurred while sequencing a message. */
    public static final int SEQUENCE_ERROR         = FATAL_ERROR + 14;

    /** An application specific non-recoverable error. */
    public static final int APP_FATAL_ERROR        = FATAL_ERROR + 50000;

    /** No error codes are allowed to be this big. */
    public static final int ERROR_LIMIT            = APP_FATAL_ERROR + 50000;

    /**
     * Translates the given error code into its symbolic name.
     *
     * @param error The error code to translate.
     * @return The symbolic name.
     */
    public static String getName(int error) {
        switch (error) {
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
        default                     : return "UNKNOWN(" + error + ")";
        }
    }
    public static boolean isFatal(int code) {
        return code >= FATAL_ERROR;
    }
    public static boolean isTransient(int code) {
        return (code >= TRANSIENT_ERROR) && (code < FATAL_ERROR);
    }
    public static boolean isMBusError(int code) {
        return ((code < APP_TRANSIENT_ERROR) && isTransient(code))
               || ((code < APP_FATAL_ERROR) && isFatal(code))
               || ((code < TRANSIENT_ERROR) && (code >= NONE));
    }
}
