// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;


import static com.yahoo.container.protect.Error.BACKEND_COMMUNICATION_ERROR;
import static com.yahoo.container.protect.Error.BAD_REQUEST;
import static com.yahoo.container.protect.Error.FORBIDDEN;
import static com.yahoo.container.protect.Error.ILLEGAL_QUERY;
import static com.yahoo.container.protect.Error.INSUFFICIENT_STORAGE;
import static com.yahoo.container.protect.Error.INTERNAL_SERVER_ERROR;
import static com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER;
import static com.yahoo.container.protect.Error.NOT_FOUND;
import static com.yahoo.container.protect.Error.NO_BACKENDS_IN_SERVICE;
import static com.yahoo.container.protect.Error.TIMEOUT;
import static com.yahoo.container.protect.Error.UNAUTHORIZED;

import java.util.Iterator;

import com.yahoo.collections.Tuple2;
import com.yahoo.container.handler.Coverage;
import com.yahoo.container.handler.Timing;
import com.yahoo.container.http.BenchmarkingHeaders;
import com.yahoo.container.logging.HitCounts;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Response;
import com.yahoo.processing.request.ErrorMessage;

/**
 * Static helper methods which implement the mapping between the ErrorMessage
 * API and HTTP headers and return codes.
 *
 * @author Einar M R Rosenvinge
 * @author Steinar Knutsen
 * @author Simon Thoresen
 * @author bratseth
 */
public final class VespaHeaders {

    // response not directly supported by JDisc core
    private static final int GATEWAY_TIMEOUT = 504;
    private static final int BAD_GATEWAY = 502;
    private static final int PRECONDITION_REQUIRED = 428;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int REQUEST_HEADER_FIELDS_TOO_LARGE = 431;
    private static final int NETWORK_AUTHENTICATION_REQUIRED = 511;

    private static final Tuple2<Boolean, Integer> NO_MATCH = new Tuple2<>(false, Response.Status.OK);

    public static boolean benchmarkCoverage(boolean benchmarkOutput, HeaderFields headers) {
        if (benchmarkOutput && headers.get(BenchmarkingHeaders.REQUEST_COVERAGE) != null) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean benchmarkOutput(com.yahoo.container.jdisc.HttpRequest request) {
        return request.getHeader(BenchmarkingHeaders.REQUEST) != null;
    }

    /**
     * Add search benchmark output to the HTTP getHeaders
     *
     * @param responseHeaders   The response to write the headers to.
     * @param benchmarkCoverage True to include coverage headers.
     * @param t                 The Timing to read data from.
     * @param c                 The Counts to read data from.
     * @param errorCount        The error count.
     * @param coverage          The Coverage to read data from.
     */
    public static void benchmarkOutput(HeaderFields responseHeaders, boolean benchmarkCoverage,
                                       Timing t, HitCounts c, int errorCount, Coverage coverage)
    {
        final long renderStartTime = System.currentTimeMillis();
        if (c != null) {
            // Fill inn response getHeaders
            responseHeaders.add(BenchmarkingHeaders.NUM_HITS, String.valueOf(c.getRetrievedHitCount()));
            responseHeaders.add(BenchmarkingHeaders.NUM_FASTHITS, String.valueOf(c.getSummaryCount()));
            responseHeaders.add(BenchmarkingHeaders.TOTAL_HIT_COUNT, String.valueOf(c.getTotalHitCount()));
            responseHeaders.add(BenchmarkingHeaders.QUERY_HITS, String.valueOf(c.getRequestedHits()));
            responseHeaders.add(BenchmarkingHeaders.QUERY_OFFSET, String.valueOf(c.getRequestedOffset()));
        }
        responseHeaders.add(BenchmarkingHeaders.NUM_ERRORS, String.valueOf(errorCount));
        if (t != null) {
            if (t.getSummaryStartTime() != 0) {
                responseHeaders.add(BenchmarkingHeaders.SEARCH_TIME,
                                    String.valueOf(t.getSummaryStartTime() - t.getQueryStartTime()));
                responseHeaders.add(BenchmarkingHeaders.ATTR_TIME, "0");
                responseHeaders.add(BenchmarkingHeaders.FILL_TIME,
                                    String.valueOf(renderStartTime - t.getSummaryStartTime()));
            } else {
                responseHeaders.add(BenchmarkingHeaders.SEARCH_TIME,
                                    String.valueOf(renderStartTime - t.getQueryStartTime()));
                responseHeaders.add(BenchmarkingHeaders.ATTR_TIME, "0");
                responseHeaders.add(BenchmarkingHeaders.FILL_TIME, "0");
            }
        }

        if (benchmarkCoverage && coverage != null) {
            responseHeaders.add(BenchmarkingHeaders.DOCS_SEARCHED, String.valueOf(coverage.getDocs()));
            responseHeaders.add(BenchmarkingHeaders.NODES_SEARCHED, String.valueOf(coverage.getNodes()));
            responseHeaders.add(BenchmarkingHeaders.FULL_COVERAGE, String.valueOf(coverage.getFull() ? 1 : 0));
        }
    }

    /**
     * (during normal execution) return 200 unless this is not a success or a 4xx error is requested.
     *
     * @param isSuccess Whether or not the response represents a success.
     * @param mainError The main error of the response, if any.
     * @param allErrors All the errors of the response, if any.
     * @return The status code of the given response.
     */
    public static int getStatus(boolean isSuccess, ErrorMessage mainError, Iterator<? extends ErrorMessage> allErrors) {
        // Do note, SearchResponse has its own implementation of isSuccess()
        if (isSuccess) {
            Tuple2<Boolean, Integer> status = webServiceCodes(mainError, allErrors);
            if (status.first) {
                return status.second;
            } else {
                return Response.Status.OK;
            }
        }
        return getEagerErrorStatus(mainError, allErrors);
    }

    private static Tuple2<Boolean, Integer> webServiceCodes(ErrorMessage mainError, Iterator<? extends ErrorMessage> allErrors) {
        if (mainError == null) return NO_MATCH;

        Iterator<? extends ErrorMessage> errorIterator = allErrors;
        if (errorIterator != null && errorIterator.hasNext()) {
            for (; errorIterator.hasNext();) {
                ErrorMessage error = errorIterator.next();
                Tuple2<Boolean, Integer> status = chooseWebServiceStatus(error);
                if (status.first) {
                    return status;
                }
            }
        } else {
            Tuple2<Boolean, Integer> status = chooseWebServiceStatus(mainError);
            if (status.first) {
                return status;
            }
        }
        return NO_MATCH;
    }


    private static Tuple2<Boolean, Integer> chooseWebServiceStatus(ErrorMessage error) {
        if (isHttpStatusCode(error.getCode()))
            return new Tuple2<>(true, error.getCode());
        if (error.getCode() == FORBIDDEN.code)
            return new Tuple2<>(true, Response.Status.FORBIDDEN);
        if (error.getCode() == UNAUTHORIZED.code)
            return new Tuple2<>(true, Response.Status.UNAUTHORIZED);
        if (error.getCode() == NOT_FOUND.code)
            return new Tuple2<>(true, Response.Status.NOT_FOUND);
        if (error.getCode() == BAD_REQUEST.code)
            return new Tuple2<>(true, Response.Status.BAD_REQUEST);
        if (error.getCode() == INTERNAL_SERVER_ERROR.code)
            return new Tuple2<>(true, Response.Status.INTERNAL_SERVER_ERROR);
        if (error.getCode() == INSUFFICIENT_STORAGE.code)
            return new Tuple2<>(true, Response.Status.INSUFFICIENT_STORAGE);
        return NO_MATCH;
    }

    // TODO: The status codes in jDisc should be an ENUM so we can enumerate the values
    private static boolean isHttpStatusCode(int code) {
        switch (code) {
            case Response.Status.OK :
            case Response.Status.MOVED_PERMANENTLY :
            case Response.Status.FOUND :
            case Response.Status.TEMPORARY_REDIRECT :
            case Response.Status.BAD_REQUEST :
            case Response.Status.UNAUTHORIZED  :
            case Response.Status.FORBIDDEN :
            case Response.Status.NOT_FOUND :
            case Response.Status.METHOD_NOT_ALLOWED :
            case Response.Status.NOT_ACCEPTABLE :
            case Response.Status.REQUEST_TIMEOUT :
            case Response.Status.INTERNAL_SERVER_ERROR :
            case Response.Status.NOT_IMPLEMENTED :
            case Response.Status.SERVICE_UNAVAILABLE :
            case Response.Status.VERSION_NOT_SUPPORTED :
            case GATEWAY_TIMEOUT :
            case BAD_GATEWAY :
            case PRECONDITION_REQUIRED :
            case TOO_MANY_REQUESTS :
            case REQUEST_HEADER_FIELDS_TOO_LARGE :
            case NETWORK_AUTHENTICATION_REQUIRED :
                return true;
            default:
                return false;
        }
    }


    /**
     * Returns 5xx or 4xx if there is any error present in the result, 200 otherwise
     *
     * @param mainError The main error of the response.
     * @param allErrors All the errors of the response, if any.
     * @return The error status code of the given response.
     */
    public static int getEagerErrorStatus(ErrorMessage mainError, Iterator<? extends ErrorMessage> allErrors) {
        if (mainError == null ) return Response.Status.OK;

        // Iterate over all errors
        if (allErrors != null && allErrors.hasNext()) {
            for (; allErrors.hasNext();) {
                ErrorMessage error = allErrors.next();
                Tuple2<Boolean, Integer> status = chooseStatusFromError(error);
                if (status.first) {
                    return status.second;
                }
            }
        } else {
            Tuple2<Boolean, Integer> status = chooseStatusFromError(mainError);
            if (status.first) {
                return status.second;
            }
        }

        // Default return code for errors
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static Tuple2<Boolean, Integer> chooseStatusFromError(ErrorMessage error) {

        Tuple2<Boolean, Integer> webServiceStatus = chooseWebServiceStatus(error);
        if (webServiceStatus.first) {
            return webServiceStatus;
        }
        if (error.getCode() == NO_BACKENDS_IN_SERVICE.code)
            return new Tuple2<>(true, Response.Status.SERVICE_UNAVAILABLE);
        if (error.getCode() == TIMEOUT.code)
            return new Tuple2<>(true, Response.Status.GATEWAY_TIMEOUT);
        if (error.getCode() == BACKEND_COMMUNICATION_ERROR.code)
            return new Tuple2<>(true, Response.Status.SERVICE_UNAVAILABLE);
        if (error.getCode() == ILLEGAL_QUERY.code)
            return new Tuple2<>(true, Response.Status.BAD_REQUEST);
        if (error.getCode() == INVALID_QUERY_PARAMETER.code)
            return new Tuple2<>(true, Response.Status.BAD_REQUEST);
        return NO_MATCH;
    }

}
