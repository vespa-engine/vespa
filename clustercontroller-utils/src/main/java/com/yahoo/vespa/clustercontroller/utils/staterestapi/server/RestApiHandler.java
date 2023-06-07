// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.server;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.time.TimeBudget;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequestHandler;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;
import com.yahoo.vespa.clustercontroller.utils.communication.http.JsonHttpResult;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.StateRestAPI;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.DeadlineExceededException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidOptionValueException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OtherMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.UnknownMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.UnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.SetResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RestApiHandler implements HttpRequestHandler {

    public static final Duration MAX_TIMEOUT = Duration.ofHours(1);

    private final static Logger log = Logger.getLogger(RestApiHandler.class.getName());

    private final StateRestAPI restApi;
    private final JsonWriter jsonWriter;
    private final JsonReader jsonReader = new JsonReader();
    private final Clock clock;

    public RestApiHandler(StateRestAPI restApi) {
        this.restApi = restApi;
        this.jsonWriter = new JsonWriter();
        this.clock = Clock.systemUTC();
    }

    public RestApiHandler setDefaultPathPrefix(String defaultPathPrefix) {
        jsonWriter.setDefaultPathPrefix(defaultPathPrefix);
        return this;
    }

    private static void logRequestException(HttpRequest request, Exception exception, Level level) {
        String exceptionString = Exceptions.toMessageString(exception);
        log.log(level, "Failed to process request with URI path " + request.getPath() + ": " + exceptionString);
    }

    @Override
    public HttpResult handleRequest(HttpRequest request) {
        Instant start = clock.instant();

        try{
            List<String> unitPath = createUnitPath(request);
            if (request.getHttpOperation().equals(HttpRequest.HttpOp.GET)) {
                final int recursiveLevel = getRecursiveLevel(request);
                UnitResponse data = restApi.getState(new UnitStateRequest() {
                    @Override
                    public int getRecursiveLevels() {
                        return recursiveLevel;
                    }
                    @Override
                    public List<String> getUnitPath() { return unitPath; }
                });
                return new JsonHttpResult().setJson(jsonWriter.createJson(data));
            } else {
                final JsonReader.SetRequestData setRequestData = jsonReader.getStateRequestData(request);
                final Optional<Duration> timeout = parseTimeout(request.getOption("timeout", null));
                SetResponse setResponse = restApi.setUnitState(new SetUnitStateRequest() {
                    @Override
                    public Map<String, UnitState> getNewState() {
                        return setRequestData.stateMap;
                    }
                    @Override
                    public List<String> getUnitPath() {
                        return unitPath;
                    }
                    @Override
                    public Condition getCondition() { return setRequestData.condition; }
                    @Override
                    public ResponseWait getResponseWait() { return setRequestData.responseWait; }
                    @Override
                    public TimeBudget timeBudget() { return TimeBudget.from(clock, start, timeout); }
                    @Override
                    public boolean isProbe() { return setRequestData.probe; }
                });
                return new JsonHttpResult().setJson(jsonWriter.createJson(setResponse));
            }
        } catch (OtherMasterException exception) {
            logRequestException(request, exception, Level.FINE);
            JsonHttpResult result = new JsonHttpResult();
            result.setHttpCode(307, "Temporary Redirect");
            result.addHeader("Location", getMasterLocationUrl(request, exception.getHost(), exception.getPort()));
            result.setJson(jsonWriter.createErrorJson(exception.getMessage()));
            return result;
        } catch (UnknownMasterException exception) {
            logRequestException(request, exception, Level.WARNING);
            JsonHttpResult result = new JsonHttpResult();
            result.setHttpCode(503, "Service Unavailable");
            result.setJson(jsonWriter.createErrorJson(exception.getMessage()));
            return result;
        } catch (DeadlineExceededException | UncheckedTimeoutException exception) {
            logRequestException(request, exception, Level.WARNING);
            JsonHttpResult result = new JsonHttpResult();
            result.setHttpCode(504, "Gateway Timeout");
            result.setJson(jsonWriter.createErrorJson(exception.getMessage()));
            return result;
        } catch (StateRestApiException exception) {
            logRequestException(request, exception, Level.WARNING);
            JsonHttpResult result = new JsonHttpResult();
            result.setHttpCode(500, "Failed to process request");
            if (exception.getStatus() != null) result.setHttpCode(result.getHttpReturnCode(), exception.getStatus());
            if (exception.getCode() != null) result.setHttpCode(exception.getCode(), result.getHttpReturnCodeDescription());
            result.setJson(jsonWriter.createErrorJson(exception.getMessage()));
            return result;
        } catch (Exception exception) {
            logRequestException(request, exception, Level.SEVERE);
            JsonHttpResult result = new JsonHttpResult();
            result.setHttpCode(500, "Failed to process request");
            result.setJson(jsonWriter.createErrorJson(exception.getClass().getName() + ": " + exception.getMessage()));
            return result;
        }
    }

    private List<String> createUnitPath(HttpRequest request) {
        List<String> path = List.of(request.getPath().split("/"));
        return path.subList(3, path.size());
    }

    private int getRecursiveLevel(HttpRequest request) throws StateRestApiException {
        String val = request.getOption("recursive", "false");
        if (val.toLowerCase().equals("false")) { return 0; }
        if (val.toLowerCase().equals("true")) { return Integer.MAX_VALUE; }
        int level;
        try{
            level = Integer.parseInt(val);
            if (level < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new InvalidOptionValueException(
                    "recursive", val, "Recursive option must be true, false, 0 or a positive integer");
        }
        return level;
    }

    private String getMasterLocationUrl(HttpRequest request, String host, int port) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getScheme()).append("://").append(host).append(':').append(port)
          .append(request.getPath());
        if (!request.getUrlOptions().isEmpty()) {
            boolean first = true;
            for (HttpRequest.KeyValuePair kvp : request.getUrlOptions()) {
                sb.append(first ? '?' : '&');
                first = false;
                sb.append(httpEscape(kvp.getKey())).append('=').append(httpEscape(kvp.getValue()));
            }
        }
        return sb.toString();
    }

    private static class Escape {
        public final String pattern;
        public final String replaceWith;

        public Escape(String pat, String repl) {
            this.pattern = pat;
            this.replaceWith = repl;
        }
    }
    private static List<Escape> escapes = new ArrayList<>();
    static {
        escapes.add(new Escape("%", "%25"));
        escapes.add(new Escape(" ", "%20"));
        escapes.add(new Escape("\\?", "%3F"));
        escapes.add(new Escape("=", "%3D"));
        escapes.add(new Escape("\\&", "%26"));
    }

    private static String httpEscape(String value) {
        for(Escape e : escapes) {
            value = value.replaceAll(e.pattern, e.replaceWith);
        }
        return value;
    }

    static Optional<Duration> parseTimeout(String timeoutOption) throws InvalidContentException {
        if (timeoutOption == null) {
            return Optional.empty();
        }

        float timeoutSeconds;
        try {
            timeoutSeconds = Float.parseFloat(timeoutOption);
        } catch (NumberFormatException e) {
            throw new InvalidContentException("value of timeout->" + timeoutOption + " is not a float");
        }

        if (timeoutSeconds <= 0.0) {
            return Optional.of(Duration.ZERO);
        } else if (timeoutSeconds <= MAX_TIMEOUT.getSeconds()) {
            return Optional.of(Duration.ofMillis(Math.round(timeoutSeconds * 1000)));
        } else {
            throw new InvalidContentException("value of timeout->" + timeoutOption + " exceeds max timeout " + MAX_TIMEOUT);
        }
    }
}
