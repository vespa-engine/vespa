// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * This class contains the error codes defined by the RPC
 * protocol. The error code associated with a request is obtained
 * through the {@link Request#errorCode} method. Note that according to
 * the RPC protocol, applications may define custom error codes with
 * values 65536 (0x10000) and greater.
 **/
public class ErrorCode
{
    /** No error (0) **/
    public static final int NONE            =   0;

    /** General error (100) **/
    public static final int GENERAL_ERROR   = 100;

    /** Not implemented (101) **/
    public static final int NOT_IMPLEMENTED = 101;

    /** Invocation aborted (102) **/
    public static final int ABORT           = 102;

    /** Invocation timed out (103) **/
    public static final int TIMEOUT         = 103;

    /** Connection error (104) **/
    public static final int CONNECTION      = 104;

    /** Bad request packet (105) **/
    public static final int BAD_REQUEST     = 105;

    /** No such method (106) **/
    public static final int NO_SUCH_METHOD  = 106;

    /** Illegal parameters (107) **/
    public static final int WRONG_PARAMS    = 107;

    /** Request dropped due to server overload (108) **/
    public static final int OVERLOAD        = 108;

    /** Illegal return values (109) **/
    public static final int WRONG_RETURN    = 109;

    /** Bad reply packet (110) **/
    public static final int BAD_REPLY       = 110;

    /** Method failed (111) **/
    public static final int METHOD_FAILED   = 111;

    /** Permission denied (112) **/
    public static final int PERMISSION_DENIED = 112;
}
