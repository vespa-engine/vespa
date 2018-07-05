// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import org.eclipse.jetty.http.MimeTypes;

import javax.annotation.concurrent.GuardedBy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.CompletionHandlerUtils.NOOP_COMPLETION_HANDLER;

/**
 * @author Tony Vaagenes
 * @author bjorncs
 */
public class ServletResponseController {

    private static Logger log = Logger.getLogger(ServletResponseController.class.getName());

    /**
     * The servlet spec does not require (Http)ServletResponse nor ServletOutputStream to be thread-safe. Therefore,
     * we must provide our own synchronization, since we may attempt to access these objects simultaneously from
     * different threads. (The typical cause of this is when one thread is writing a response while another thread
     * throws an exception, causing the request to fail with an error response).
     */
    private final Object monitor = new Object();

    //servletResponse must not be modified after the response has been committed.
    private final HttpServletRequest servletRequest;
    private final HttpServletResponse servletResponse;
    private final boolean developerMode;
    private final ErrorResponseContentCreator errorResponseContentCreator = new ErrorResponseContentCreator();

    //all calls to the servletOutputStreamWriter must hold the monitor first to ensure visibility of servletResponse changes.
    private final ServletOutputStreamWriter servletOutputStreamWriter;

    @GuardedBy("monitor")
    private boolean responseCommitted = false;

    public ServletResponseController(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse,
            Executor executor,
            MetricReporter metricReporter,
            boolean developerMode) throws IOException {

        this.servletRequest = servletRequest;
        this.servletResponse = servletResponse;
        this.developerMode = developerMode;
        this.servletOutputStreamWriter =
                new ServletOutputStreamWriter(servletResponse.getOutputStream(), executor, metricReporter);
    }


    private static int getStatusCode(Throwable t) {
        if (t instanceof BindingNotFoundException) {
            return HttpServletResponse.SC_NOT_FOUND;
        } else if (t instanceof BindingSetNotFoundException) {
            return HttpServletResponse.SC_NOT_FOUND;
        } else if (t instanceof RequestException) {
            return ((RequestException)t).getResponseStatus();
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


    public void trySendError(Throwable t) {
        final boolean responseWasCommitted;
        try {
            synchronized (monitor) {
                String reasonPhrase = getReasonPhrase(t, developerMode);
                int statusCode = getStatusCode(t);
                responseWasCommitted = responseCommitted;
                if (!responseCommitted) {
                    responseCommitted = true;
                    sendErrorAsync(statusCode, reasonPhrase);
                }
            }
        } catch (Throwable e) {
            servletOutputStreamWriter.fail(t);
            return;
        }

        //Must be evaluated after state transition for test purposes(See ConformanceTestException)
        //Done outside the monitor since it causes a callback in tests.
        if (responseWasCommitted) {
            RuntimeException exceptionWithStackTrace = new RuntimeException(t);
            log.log(Level.FINE, "Response already committed, can't change response code", exceptionWithStackTrace);
            // TODO: should always have failed here, but that breaks test assumptions. Doing soft close instead.
            //assert !Thread.holdsLock(monitor);
            //servletOutputStreamWriter.fail(t);
            servletOutputStreamWriter.close();
        }

    }

    /**
     * Async version of {@link org.eclipse.jetty.server.Response#sendError(int, String)}.
     */
    private void sendErrorAsync(int statusCode, String reasonPhrase) {
        servletResponse.setHeader(HttpHeaders.Names.EXPIRES, null);
        servletResponse.setHeader(HttpHeaders.Names.LAST_MODIFIED, null);
        servletResponse.setHeader(HttpHeaders.Names.CACHE_CONTROL, null);
        servletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, null);
        servletResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, null);
        setStatus(servletResponse, statusCode, Optional.of(reasonPhrase));

        // If we are allowed to have a body
        if (statusCode != HttpServletResponse.SC_NO_CONTENT &&
                statusCode != HttpServletResponse.SC_NOT_MODIFIED &&
                statusCode != HttpServletResponse.SC_PARTIAL_CONTENT &&
                statusCode >= HttpServletResponse.SC_OK) {
            servletResponse.setHeader(HttpHeaders.Names.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
            servletResponse.setContentType(MimeTypes.Type.TEXT_HTML_8859_1.toString());
            byte[] errorContent = errorResponseContentCreator
                    .createErrorContent(servletRequest.getRequestURI(), statusCode, Optional.ofNullable(reasonPhrase));
            servletResponse.setContentLength(errorContent.length);
            servletOutputStreamWriter.sendErrorContentAndCloseAsync(ByteBuffer.wrap(errorContent));
        } else {
            servletResponse.setContentLength(0);
            servletOutputStreamWriter.close();
        }
    }

    /**
     * When this future completes there will be no more calls against the servlet output stream or servlet response.
     * The framework is still allowed to invoke us though.
     *
     * The future might complete in the servlet framework thread, user thread or executor thread.
     */
    public CompletableFuture<Void> finishedFuture() {
        return servletOutputStreamWriter.finishedFuture;
    }

    private void setResponse(Response jdiscResponse) {
        synchronized (monitor) {
            if (responseCommitted) {
                log.log(Level.FINE,
                        jdiscResponse.getError(),
                        () -> "Response already committed, can't change response code. " +
                                "From: " + servletResponse.getStatus() + ", To: " + jdiscResponse.getStatus());

                //TODO: should throw an exception here, but this breaks unit tests.
                //The failures will now instead happen when writing buffers.
                servletOutputStreamWriter.close();
                return;
            }

            setStatus_holdingLock(jdiscResponse, servletResponse);
            setHeaders_holdingLock(jdiscResponse, servletResponse);
        }
    }

    private static void setHeaders_holdingLock(Response jdiscResponse, HttpServletResponse servletResponse) {
        for (final Map.Entry<String, String> entry : jdiscResponse.headers().entries()) {
            servletResponse.addHeader(entry.getKey(), entry.getValue());
        }

        if (servletResponse.getContentType() == null) {
            servletResponse.setContentType("text/plain;charset=utf-8");
        }
    }

    private static void setStatus_holdingLock(Response jdiscResponse, HttpServletResponse servletResponse) {
        if (jdiscResponse instanceof HttpResponse) {
            setStatus(servletResponse, jdiscResponse.getStatus(), Optional.ofNullable(((HttpResponse) jdiscResponse).getMessage()));
        } else {
            setStatus(servletResponse, jdiscResponse.getStatus(), getErrorMessage(jdiscResponse));
        }
    }

    @SuppressWarnings("deprecation")
    private static void setStatus(HttpServletResponse response, int statusCode, Optional<String> reasonPhrase) {
        if (reasonPhrase.isPresent()) {
            // Sets the status line: a status code along with a custom message.
            // Using a custom status message is deprecated in the Servlet API. No alternative exist.
            response.setStatus(statusCode, reasonPhrase.get()); // DEPRECATED
        } else {
            response.setStatus(statusCode);
        }
    }

    private static Optional<String> getErrorMessage(Response jdiscResponse) {
        return Optional.ofNullable(jdiscResponse.getError()).flatMap(
                error -> Optional.ofNullable(error.getMessage()));
    }


    private void commitResponse() {
        synchronized (monitor) {
            responseCommitted = true;
        }
    }

    public final ResponseHandler responseHandler = new ResponseHandler() {
        @Override
        public ContentChannel handleResponse(Response response) {
            setResponse(response);
            return responseContentChannel;
        }
    };

    public final ContentChannel responseContentChannel = new ContentChannel() {
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            commitResponse();
            servletOutputStreamWriter.writeBuffer(buf, handlerOrNoopHandler(handler));
        }

        @Override
        public void close(CompletionHandler handler) {
            commitResponse();
            servletOutputStreamWriter.close(handlerOrNoopHandler(handler));
        }

        private CompletionHandler handlerOrNoopHandler(CompletionHandler handler) {
            return handler != null ? handler : NOOP_COMPLETION_HANDLER;
        }
    };
}
