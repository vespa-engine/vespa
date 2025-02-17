// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.MimeTypes;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.CompletionHandlerUtils.NOOP_COMPLETION_HANDLER;

/**
 * @author Tony Vaagenes
 * @author bjorncs
 */
class ServletResponseController {

    private enum State {
        WAITING_FOR_RESPONSE,
        ACCEPTED_RESPONSE_FROM_HANDLER,
        COMMITTED_RESPONSE_FROM_HANDLER,
        COMPLETED_WITH_RESPONSE_FROM_HANDLER,
        COMPLETED_WITH_ERROR_RESPONSE
    }

    private static final Logger log = Logger.getLogger(ServletResponseController.class.getName());

    /**
     * Only a single thread must modify {@link HttpServletRequest}/{@link HttpServletResponse} at a time,
     * and it must only be performed when the response is committed.
     * The response cannot be modified once response content is being written.
     */
    private final Object monitor = new Object();

    private final HttpServletRequest servletRequest;
    private final HttpServletResponse servletResponse;
    private final boolean developerMode;
    private final ErrorResponseContentCreator errorResponseContentCreator = new ErrorResponseContentCreator();
    private final ServletOutputStreamWriter out;

    // GuardedBy("monitor")
    private State state = State.WAITING_FOR_RESPONSE;
    private Response handlerResponse;

    ServletResponseController(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse,
            Janitor janitor,
            RequestMetricReporter metricReporter,
            boolean developerMode) throws IOException {

        this.servletRequest = servletRequest;
        this.servletResponse = servletResponse;
        this.developerMode = developerMode;
        this.out = new ServletOutputStreamWriter(servletResponse.getOutputStream(), janitor, metricReporter);
    }

    /** Try to send an error response (assuming failure is recoverable) */
    void trySendErrorResponse(Throwable t) {
        synchronized (monitor) {
            try {
                switch (state) {
                    case WAITING_FOR_RESPONSE:
                    case ACCEPTED_RESPONSE_FROM_HANDLER:
                        state = State.COMPLETED_WITH_ERROR_RESPONSE;
                        break;
                    case COMMITTED_RESPONSE_FROM_HANDLER:
                    case COMPLETED_WITH_RESPONSE_FROM_HANDLER:
                        if (log.isLoggable(Level.FINE)) {
                            RuntimeException exceptionWithStackTrace = new RuntimeException(t);
                            log.log(Level.FINE, "Response already committed, can't change response code", exceptionWithStackTrace);
                        }
                        return;
                    case COMPLETED_WITH_ERROR_RESPONSE:
                        return;
                    default:
                        throw new IllegalStateException();
                }
                writeErrorResponse(t);
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            } finally {
                out.close();
            }
        }
    }

    /** Close response writer and fail out any queued response content */
    void forceClose(Throwable t) { out.fail(t); }

    /**
     * When this future completes there will be no more calls against the servlet output stream or servlet response.
     * The framework is still allowed to invoke us though.
     *
     * The future might complete in the servlet framework thread, user thread or executor thread.
     */
    CompletableFuture<Void> finishedFuture() { return out.finishedFuture(); }

    ResponseHandler responseHandler() { return responseHandler; }

    private void writeErrorResponse(Throwable t) {
        servletResponse.setHeader(HttpHeaders.Names.EXPIRES, null);
        servletResponse.setHeader(HttpHeaders.Names.LAST_MODIFIED, null);
        servletResponse.setHeader(HttpHeaders.Names.CACHE_CONTROL, null);
        servletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, null);
        servletResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, null);
        String reasonPhrase = getReasonPhrase(t, developerMode);
        int statusCode = getStatusCode(t);
        setStatus(servletResponse, statusCode, reasonPhrase);
        // If we are allowed to have a body
        if (statusCode != HttpServletResponse.SC_NO_CONTENT &&
                statusCode != HttpServletResponse.SC_NOT_MODIFIED &&
                statusCode != HttpServletResponse.SC_PARTIAL_CONTENT &&
                statusCode >= HttpServletResponse.SC_OK) {
            servletResponse.setHeader(HttpHeaders.Names.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
            servletResponse.setContentType(MimeTypes.Type.TEXT_HTML_8859_1.toString());
            byte[] errorContent = errorResponseContentCreator
                    .createErrorContent(servletRequest.getRequestURI(), statusCode, reasonPhrase);
            servletResponse.setContentLength(errorContent.length);
            out.writeBuffer(ByteBuffer.wrap(errorContent), NOOP_COMPLETION_HANDLER);
        } else {
            servletResponse.setContentLength(0);
        }
    }

    private static int getStatusCode(Throwable t) {
        if (t instanceof BindingNotFoundException) {
            return HttpServletResponse.SC_NOT_FOUND;
        } else if (t instanceof BindingSetNotFoundException) {
            return HttpServletResponse.SC_NOT_FOUND;
        } else if (t instanceof RequestException) {
            return ((RequestException)t).getResponseStatus();
        } else if (t instanceof TimeoutException) {
            // E.g stream idle timeout for HTTP/2
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        } else {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    private static String getReasonPhrase(Throwable t, boolean developerMode) {
        if (developerMode) {
            final StringWriter out = new StringWriter();
            t.printStackTrace(new PrintWriter(out));
            return out.toString();
        } else if (t.getMessage() != null) {
            return t.getMessage();
        } else {
            return t.toString();
        }
    }

    private void acceptResponseFromHandler(Response response) {
        synchronized (monitor) {
            switch (state) {
                case WAITING_FOR_RESPONSE:
                case ACCEPTED_RESPONSE_FROM_HANDLER: // Allow multiple invocations to ResponseHandler.handleResponse()
                    handlerResponse = response;
                    state = State.ACCEPTED_RESPONSE_FROM_HANDLER;
                    servletRequest.setAttribute(
                            ResponseMetricAggregator.requestTypeAttribute, handlerResponse.getRequestType());
                    return;
                case COMMITTED_RESPONSE_FROM_HANDLER:
                case COMPLETED_WITH_RESPONSE_FROM_HANDLER:
                    String message = "Response already committed, can't change response code. " +
                            "From: " + servletResponse.getStatus() + ", To: " + response.getStatus();
                    log.log(Level.FINE, message, response.getError());
                    throw new IllegalStateException(message);
                case COMPLETED_WITH_ERROR_RESPONSE:
                    log.log(Level.FINE, "Error response already written");
                    return; // Silently ignore response from handler when request was failed out
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private static void setStatus(HttpServletResponse response, int statusCode, String reasonPhrase) {
        org.eclipse.jetty.server.Response jettyResponse = (org.eclipse.jetty.server.Response) response;
        if (reasonPhrase != null) {
            jettyResponse.setStatusWithReason(statusCode, reasonPhrase);
        } else {
            jettyResponse.setStatus(statusCode);
        }
    }


    private void commitResponseFromHandlerIfUncommitted(boolean close) {
        synchronized (monitor) {
            switch (state) {
                case ACCEPTED_RESPONSE_FROM_HANDLER:
                    state = close ? State.COMPLETED_WITH_RESPONSE_FROM_HANDLER : State.COMMITTED_RESPONSE_FROM_HANDLER;
                    break;
                case WAITING_FOR_RESPONSE:
                    throw new IllegalStateException("No response provided");
                case COMMITTED_RESPONSE_FROM_HANDLER:
                case COMPLETED_WITH_RESPONSE_FROM_HANDLER:
                    return;
                case COMPLETED_WITH_ERROR_RESPONSE:
                    log.fine("An error response is already committed - failure will be handled by ServletOutputStreamWriter");
                    return;
                default:
                    throw new IllegalStateException();
            }
            if (handlerResponse instanceof HttpResponse) {
                setStatus(servletResponse, handlerResponse.getStatus(), ((HttpResponse) handlerResponse).getMessage());
            } else {
                String message = Optional.ofNullable(handlerResponse.getError())
                        .flatMap(error -> Optional.ofNullable(error.getMessage()))
                        .orElse(null);
                setStatus(servletResponse, handlerResponse.getStatus(), message);
            }
            for (final Map.Entry<String, String> entry : handlerResponse.headers().entries()) {
                servletResponse.addHeader(entry.getKey(), entry.getValue());
            }
            if (servletResponse.getContentType() == null) {
                servletResponse.setContentType("text/plain;charset=utf-8");
            }
        }
    }

    private final ResponseHandler responseHandler = new ResponseHandler() {
        @Override
        public ContentChannel handleResponse(Response response) {
            acceptResponseFromHandler(response);
            return responseContentChannel;
        }
    };

    private final ContentChannel responseContentChannel = new ContentChannel() {
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            var wrapped = handlerOrNoopHandler(handler);
            try {
                commitResponseFromHandlerIfUncommitted(false);
                out.writeBuffer(buf, wrapped);
            } catch (Throwable t) {
                // In case any of the servlet API methods fails with exception
                log.log(Level.FINE, "Failed to write buffer to output stream", t);
                wrapped.failed(t);
            }
        }

        @Override
        public void close(CompletionHandler handler) {
            var wrapped = handlerOrNoopHandler(handler);
            try {
                commitResponseFromHandlerIfUncommitted(true);
                out.close(wrapped);
            } catch (Throwable t) {
                // In case any of the servlet API methods fails with exception
                log.log(Level.FINE, "Failed to close output stream", t);
                wrapped.failed(t);
            }
        }

        private static CompletionHandler handlerOrNoopHandler(CompletionHandler handler) {
            return handler != null ? handler : NOOP_COMPLETION_HANDLER;
        }
    };
}
