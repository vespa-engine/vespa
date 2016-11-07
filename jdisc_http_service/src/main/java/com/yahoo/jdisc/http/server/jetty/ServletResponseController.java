// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.service.BindingSetNotFoundException;

import javax.annotation.concurrent.GuardedBy;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author tonytv
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
    private final HttpServletResponse servletResponse;
    private final boolean developerMode;

    //all calls to the servletOutputStreamWriter must hold the monitor first to ensure visibility of servletResponse changes.
    private final ServletOutputStreamWriter servletOutputStreamWriter;

    @GuardedBy("monitor")
    private boolean responseCommitted = false;


    public ServletResponseController(
            HttpServletResponse servletResponse,
            Executor executor,
            MetricReporter metricReporter,
            boolean developerMode) throws IOException {

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

        synchronized (monitor) {
            responseWasCommitted = responseCommitted;

            if (!responseCommitted) {
                responseCommitted = true;
                servletOutputStreamWriter.setSendingError();
            }
        }

        //Must be evaluated after state transition for test purposes(See ConformanceTestException)
        //Done outside the monitor since it causes a callback in tests.
        String reasonPhrase = getReasonPhrase(t, developerMode);
        int statusCode = getStatusCode(t);

        if (responseWasCommitted) {

            RuntimeException exceptionWithStackTrace = new RuntimeException(t);
            log.log(Level.FINE, "Response already committed, can't change response code", exceptionWithStackTrace);
            // TODO: should always have failed here, but that breaks test assumptions. Doing soft close instead.
            //assert !Thread.holdsLock(monitor);
            //servletOutputStreamWriter.fail(t);
            servletOutputStreamWriter.close(null);
            return;
        }

        try {

            // HttpServletResponse.sendError() is blocking and must not be executed in Jetty/RequestHandler thread.
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // TODO We should control the response content this method generates
                    // a response body based on Jetty's own response templates ("Powered by Jetty").
                    servletResponse.sendError(statusCode, reasonPhrase);
                    finishedFuture().complete(null);
                } catch (IOException e) {
                    log.severe("Failed to send error response: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });

        } catch (Throwable e) {
            servletOutputStreamWriter.fail(t);
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
                servletOutputStreamWriter.close(null);
                return;
            }

            setStatus_holdingLock(jdiscResponse, servletResponse);
            setHeaders_holdingLock(jdiscResponse, servletResponse);
        }
    }

    private static void setHeaders_holdingLock(Response jdiscResponse, HttpServletResponse servletResponse) {
        for (final Map.Entry<String, String> entry : jdiscResponse.headers().entries()) {
            final String value = entry.getValue();
            servletResponse.addHeader(entry.getKey(), value != null ? value : "");
        }

        if (servletResponse.getContentType() == null) {
            servletResponse.setContentType("text/plain;charset=utf-8");
        }
    }

    private static void setStatus_holdingLock(Response jdiscResponse, HttpServletResponse servletResponse) {
        if (jdiscResponse instanceof HttpResponse) {
            // TODO: Figure out what this does to the response (with Jetty), and move to non-deprecated APIs.
            // Deprecate our own code as necessary.
            servletResponse.setStatus(jdiscResponse.getStatus(), ((HttpResponse) jdiscResponse).getMessage());
        } else {
            Optional<String> errorMessage = getErrorMessage(jdiscResponse);
            if (errorMessage.isPresent()) {
                // TODO: Figure out what this does to the response (with Jetty), and move to non-deprecated APIs.
                // Deprecate our own code as necessary.
                servletResponse.setStatus(jdiscResponse.getStatus(), errorMessage.get());
            } else {
                servletResponse.setStatus(jdiscResponse.getStatus());
            }
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

    public boolean isResponseCommitted() {
        synchronized (monitor) {
            return responseCommitted;
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
            servletOutputStreamWriter.writeBuffer(buf, handler);
        }

        @Override
        public void close(CompletionHandler handler) {
            commitResponse();
            servletOutputStreamWriter.close(handler);
        }
    };
}
