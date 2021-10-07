// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.handlers;

import com.yahoo.vespa.http.client.core.Encoder;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.27
 */
public class V3MockParsingRequestHandler extends AbstractHandler {
    private final int responseCode;
    private volatile Scenario scenario;
    private final BlockingQueue<CountDownLatch> delayedRequests = new LinkedBlockingQueue<>();
    private final AtomicBoolean delayedResponseShouldBlock = new AtomicBoolean(true);
    public final AtomicBoolean badRequestScenarioShouldReturnBadRequest = new AtomicBoolean(false);
    private final String name;
    private static final AtomicInteger sessionIdGenerator = new AtomicInteger(0);
    private AtomicInteger internalCounter = new AtomicInteger(0);

    public enum Scenario {
        ALL_OK, RETURN_WRONG_SESSION_ID,
        DISCONNECT_IMMEDIATELY, DONT_ACCEPT_VERSION, RETURN_UNEXPECTED_VERSION,
        INTERNAL_SERVER_ERROR, COULD_NOT_FEED, MBUS_RETURNED_ERROR,
        NEVER_RETURN_ANY_RESULTS, DELAYED_RESPONSE, BAD_REQUEST, SERVER_ERROR_TWICE_THEN_OK,
        EXPECT_HIGHEST_PRIORITY_AND_TRACELEVEL_123, CONDITON_NOT_MET
    }

    public V3MockParsingRequestHandler() {
        this("", HttpServletResponse.SC_OK, Scenario.ALL_OK);
    }

    public V3MockParsingRequestHandler(String name) {
        this(name, HttpServletResponse.SC_OK, Scenario.ALL_OK);
    }

    public V3MockParsingRequestHandler(int responseCode) {
        this("", responseCode, Scenario.ALL_OK);
    }

    public V3MockParsingRequestHandler(int responseCode, Scenario scenario) {
        this("", responseCode, scenario);
    }

    public V3MockParsingRequestHandler(String name, int responseCode, Scenario scenario) {
        this.name = name;
        this.responseCode = responseCode;
        this.scenario = scenario;
    }

    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        System.err.println("Server " + name + " got request from: " + request.getHeader(Headers.SESSION_ID));
        switch (scenario) {
            case ALL_OK:
                allOk(baseRequest, request, response);
                break;
            case RETURN_WRONG_SESSION_ID:
                wrongSessionId(baseRequest, request, response);
                break;
            case DISCONNECT_IMMEDIATELY:
                disconnect(baseRequest, response);
                break;
            case DONT_ACCEPT_VERSION:
                dontAcceptVersion(baseRequest, request, response);
                break;
            case RETURN_UNEXPECTED_VERSION:
                unexpectedVersion(baseRequest, request, response);
                break;
            case INTERNAL_SERVER_ERROR:
                internalServerError(baseRequest, request, response);
                break;
            case COULD_NOT_FEED:
                couldNotFeed(baseRequest, request, response);
                break;
            case MBUS_RETURNED_ERROR:
                mbusReturnedError(baseRequest, request, response);
                break;
            case NEVER_RETURN_ANY_RESULTS:
                neverReturnAnyResults(baseRequest, request, response);
                break;
            case DELAYED_RESPONSE:
                delayedResponse(baseRequest, request, response);
                break;
            case BAD_REQUEST:
                badRequest(baseRequest, request, response);
                break;
            case SERVER_ERROR_TWICE_THEN_OK:
                int state = internalCounter.getAndIncrement();
                if (state >= 2) {
                    allOk(baseRequest, request, response);
                } else {
                    couldNotFeed(baseRequest, request, response);
                }
                break;
            case EXPECT_HIGHEST_PRIORITY_AND_TRACELEVEL_123:
                checkIfSessionThenHighPriorityAndTraceLevel123(request);
                allOk(baseRequest, request, response);
                break;
            case CONDITON_NOT_MET:
                conditionNotMetRequest(baseRequest, request, response);
                break;
            default:
                throw new IllegalArgumentException("Test scenario " + scenario + " not supported.");
        }
    }

    private void checkIfSessionThenHighPriorityAndTraceLevel123(HttpServletRequest request) {
        if (request.getHeader(Headers.SESSION_ID) != null) {
            assert (request.getHeader(Headers.PRIORITY).equals("HIGHEST"));
            assert (request.getHeader(Headers.TRACE_LEVEL).equals("123"));
        }
    }

    private void conditionNotMetRequest(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = getSessionId(request);
        setHeaders(response, sessionId);
        response.setStatus(responseCode);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        String operationId;
        while ((operationId = readOperationId(request.getInputStream())) != null) {
            long lengthToSkip = readByteLength(request.getInputStream());
            while (lengthToSkip > 0) {
                long skipped = request.getInputStream().skip(lengthToSkip);
                lengthToSkip -= skipped;
            }
            respondConditionNotMet(responseWriter, operationId);
        }
        closeChannel(responseWriter);

    }
    private void badRequest(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (badRequestScenarioShouldReturnBadRequest.get()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            baseRequest.setHandled(true);
            PrintWriter responseWriter = response.getWriter();
            BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
            while (reader.readLine() != null) {
                //consume input, not really needed?
            }
            reader.close();
            closeChannel(responseWriter);
        } else {
            allOk(baseRequest, request, response);
        }
    }

    private void delayedResponse(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (delayedResponseShouldBlock.get()) {
            CountDownLatch latch = new CountDownLatch(1);
            delayedRequests.add(latch);
            try {
                latch.await(120, TimeUnit.SECONDS);  //wait "forever"
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (latch.getCount() != 0L) {
                throw new RuntimeException("Delayed request handler did not get poke()d.");
            }
        } else {
        }
        allOk(baseRequest, request, response);
    }

    public void poke() throws InterruptedException {
        CountDownLatch latch = delayedRequests.poll(10, TimeUnit.SECONDS);
        latch.countDown();
    }

    public void pokeAllAndUnblockFromNowOn() {
        delayedResponseShouldBlock.set(false);
        while (!delayedRequests.isEmpty()) {
            CountDownLatch latch = delayedRequests.remove();
            latch.countDown();
        }
    }

    private void allOk(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = getSessionId(request);
        setHeaders(response, sessionId);
        response.setStatus(responseCode);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        String operationId;
        while ((operationId = readOperationId(request.getInputStream())) != null) {
            long lengthToSkip = readByteLength(request.getInputStream());
            while (lengthToSkip > 0) {
                long skipped = request.getInputStream().skip(lengthToSkip);
                lengthToSkip -= skipped;
            }
            respondOK(responseWriter, operationId);
        }
        closeChannel(responseWriter);
    }

    private void wrongSessionId(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = generateMockSessionId();
        setHeaders(response, sessionId);
        response.setStatus(responseCode);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        String operationId;
        while ((operationId = readOperationId(request.getInputStream())) != null) {
            long lengthToSkip = readByteLength(request.getInputStream());
            while (lengthToSkip > 0) {
                long skipped = request.getInputStream().skip(lengthToSkip);
                lengthToSkip -= skipped;
            }
            respondOK(responseWriter, operationId);
        }
        closeChannel(responseWriter);
    }

    private void disconnect(Request baseRequest, HttpServletResponse response) throws IOException {
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        closeChannel(responseWriter);
    }

    private void dontAcceptVersion(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = getSessionId(request);
        setHeaders(response, sessionId);
        response.setStatus(Headers.HTTP_NOT_ACCEPTABLE);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        responseWriter.write("Go away, no such version.");
        responseWriter.flush();
        closeChannel(responseWriter);
    }

    private void unexpectedVersion(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = getSessionId(request);
        response.setHeader(Headers.SESSION_ID, sessionId);
        response.setHeader(Headers.VERSION, "12345678");
        response.setStatus(responseCode);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        String operationId;
        while ((operationId = readOperationId(request.getInputStream())) != null) {
            long lengthToSkip = readByteLength(request.getInputStream());
            while (lengthToSkip > 0) {
                long skipped = request.getInputStream().skip(lengthToSkip);
                lengthToSkip -= skipped;
            }
            respondOK(responseWriter, operationId);
        }
        closeChannel(responseWriter);
    }

    private void internalServerError(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = getSessionId(request);
        setHeaders(response, sessionId);
        response.setStatus(500);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        responseWriter.write("boom");
        responseWriter.flush();
        closeChannel(responseWriter);
    }

    private void couldNotFeed(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = getSessionId(request);
        setHeaders(response, sessionId);
        response.setStatus(responseCode);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        String operationId;
        while ((operationId = readOperationId(request.getInputStream())) != null) {
            long lengthToSkip = readByteLength(request.getInputStream());
            while (lengthToSkip > 0) {
                long skipped = request.getInputStream().skip(lengthToSkip);
                lengthToSkip -= skipped;
            }
            respondTransientFailed(responseWriter, operationId);
        }
        closeChannel(responseWriter);
    }

    private void mbusReturnedError(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = getSessionId(request);
        setHeaders(response, sessionId);
        response.setStatus(responseCode);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        String operationId;
        while ((operationId = readOperationId(request.getInputStream())) != null) {
            long lengthToSkip = readByteLength(request.getInputStream());
            while (lengthToSkip > 0) {
                long skipped = request.getInputStream().skip(lengthToSkip);
                lengthToSkip -= skipped;
            }
            respondFailedWithTransitiveErrorSeenFromClient(responseWriter, operationId);
        }
        closeChannel(responseWriter);
    }

    private void neverReturnAnyResults(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionId = getSessionId(request);
        setHeaders(response, sessionId);
        response.setStatus(responseCode);
        baseRequest.setHandled(true);
        PrintWriter responseWriter = response.getWriter();
        BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
        while (reader.readLine() != null) {
            //consume input, not really needed?
        }
        reader.close();
        closeChannel(responseWriter);
    }

    void closeChannel(PrintWriter responseWriter) {
        System.err.println("Mock server " + name + " closing channel.");
        responseWriter.close();
    }

    private String readOperationId(InputStream requestInputStream) throws IOException {
        StringBuilder idBuf = new StringBuilder(100);
        int c;
        while ((c = requestInputStream.read()) != -1) {
            if (c == 32) {
                break;
            }
            idBuf.append((char) c);  //it's ASCII
        }
        if (c == -1) {
            return null;
        }
        return Encoder.decode(idBuf.toString(), new StringBuilder(idBuf.length())).toString();
    }

    private int readByteLength(InputStream requestInputStream) throws IOException {
        StringBuilder lenBuf = new StringBuilder(8);
        int c;
        while ((c = requestInputStream.read()) != -1) {
            if (c == 10) {
                break;
            }
            lenBuf.append((char) c);  //it's ASCII
        }
        if (lenBuf.length() == 0) {
            throw new IllegalStateException("Operation length missing.");
        }
        return Integer.valueOf(lenBuf.toString(), 16);
    }

    private static void setHeaders(HttpServletResponse response, String sessionId) {
        response.setHeader(Headers.SESSION_ID, sessionId);
        response.setHeader(Headers.VERSION, "3");
    }

    private void respondFailed(PrintWriter responseWriter, String docId) {
        final OperationStatus operationStatus =
                new OperationStatus("mbus returned boom", docId, ErrorCode.ERROR, false, "trace");
        writeResponse(responseWriter, operationStatus);
    }

    private void respondTransientFailed(PrintWriter responseWriter, String docId) {
        final OperationStatus operationStatus = new OperationStatus(
                "Could not put", docId, ErrorCode.TRANSIENT_ERROR, false, "");
        writeResponse(responseWriter, operationStatus);
    }

    private void respondFailedWithTransitiveErrorSeenFromClient(PrintWriter responseWriter, String docId) {
        final OperationStatus operationStatus =
                new OperationStatus("NETWORK_ERROR", docId, ErrorCode.ERROR, false, "trace");
        writeResponse(responseWriter, operationStatus);
    }

    private void respondConditionNotMet(PrintWriter responseWriter, String docId) {
        final OperationStatus operationStatus =
                new OperationStatus("this is a test", docId, ErrorCode.ERROR, true, "trace");
        writeResponse(responseWriter, operationStatus);
    }
    private void respondOK(PrintWriter responseWriter, String docId) {
        final OperationStatus operationStatus = new OperationStatus("Doc fed", docId, ErrorCode.OK, false, "Trace message");
        writeResponse(responseWriter, operationStatus);
    }

    private void writeResponse(PrintWriter responseWriter,
                               final OperationStatus operationStatus) {
        responseWriter.print(operationStatus.render());
        responseWriter.flush();
        System.err.println("Mock " + name + " server wrote: " + operationStatus.render());
    }

    private String getSessionId(HttpServletRequest request) {
        return request.getHeader(Headers.CLIENT_ID);
    }

    private String generateMockSessionId() {
        return String.valueOf(sessionIdGenerator.getAndIncrement());
    }
}
