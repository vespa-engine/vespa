// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.Response;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Reply;

/**
 * @author Simon Thoresen Hult
 */
public class StatusCodes {

    public static int fromMbusReply(final Reply reply) {
        int statusCode = Response.Status.OK;
        for (int i = 0, len = reply.getNumErrors(); i < len; ++i) {
            statusCode = Math.max(statusCode, fromMbusError(reply.getError(i)));
        }
        return statusCode;
    }

    public static int fromMbusError(final Error error) {
        final int errorCode = error.getCode();
        if (errorCode < ErrorCode.TRANSIENT_ERROR) {
            return Response.Status.OK;
        }
        if (errorCode < ErrorCode.FATAL_ERROR) {
            return Response.Status.TEMPORARY_REDIRECT;
        }
        switch (errorCode) {
        case ErrorCode.SEND_QUEUE_CLOSED:
            return Response.Status.LOCKED;
        case ErrorCode.ILLEGAL_ROUTE:
            return Response.Status.BAD_REQUEST;
        case ErrorCode.NO_SERVICES_FOR_ROUTE:
            return Response.Status.NOT_FOUND;
        case ErrorCode.ENCODE_ERROR:
            return Response.Status.BAD_REQUEST;
        case ErrorCode.NETWORK_ERROR:
            return Response.Status.BAD_REQUEST; // got nothing better
        case ErrorCode.UNKNOWN_PROTOCOL:
            return Response.Status.UNSUPPORTED_MEDIA_TYPE;
        case ErrorCode.DECODE_ERROR:
            return Response.Status.UNSUPPORTED_MEDIA_TYPE;
        case ErrorCode.TIMEOUT:
            return Response.Status.REQUEST_TIMEOUT;
        case ErrorCode.INCOMPATIBLE_VERSION:
            return Response.Status.VERSION_NOT_SUPPORTED;
        case ErrorCode.UNKNOWN_POLICY:
            return Response.Status.BAD_REQUEST;
        case ErrorCode.NETWORK_SHUTDOWN:
            return Response.Status.LOCKED;
        case ErrorCode.POLICY_ERROR:
            return Response.Status.PRECONDITION_FAILED;
        case ErrorCode.SEQUENCE_ERROR:
            return Response.Status.PRECONDITION_FAILED;
        case ErrorCode.APP_FATAL_ERROR:
            return Response.Status.INTERNAL_SERVER_ERROR;
        default:
            return Response.Status.INTERNAL_SERVER_ERROR;
        }
    }

    public static Error toMbusError(final int statusCode) {
        if (statusCode < 300) {
            return null;
        } else if (statusCode < 400) {
            return new Error(ErrorCode.APP_TRANSIENT_ERROR, statusCode + " Redirection");
        } else if (statusCode < 500) {
            return new Error(ErrorCode.APP_FATAL_ERROR, statusCode + " Client Error");
        } else if (statusCode < 600) {
            return new Error(ErrorCode.APP_FATAL_ERROR, statusCode + " Server Error");
        } else {
            return new Error(ErrorCode.APP_FATAL_ERROR, statusCode + " Unknown Error");
        }
    }
}
