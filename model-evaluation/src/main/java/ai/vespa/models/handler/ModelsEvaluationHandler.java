// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.handler;

import ai.vespa.models.evaluation.FunctionEvaluator;
import ai.vespa.models.evaluation.Model;
import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

public class ModelsEvaluationHandler extends ThreadedHttpRequestHandler {

    /** A dash in this key ensures it does not collide with feature names */
    private static final String missingValueKey = "missing-value";

    public static final String API_ROOT = "model-evaluation";
    public static final String VERSION_V1 = "v1";
    public static final String EVALUATE = "eval";

    private final ModelsEvaluator modelsEvaluator;

    public ModelsEvaluationHandler(ModelsEvaluator modelsEvaluator, Executor executor) {
        super(executor);
        this.modelsEvaluator = modelsEvaluator;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Path path = new Path(request);
        Optional<String> apiName = path.segment(0);
        Optional<String> version = path.segment(1);
        Optional<String> modelName = path.segment(2);

        try {
            if ( apiName.isEmpty() || ! apiName.get().equalsIgnoreCase(API_ROOT))
                throw new IllegalArgumentException("unknown API");
            if ( version.isEmpty() || ! version.get().equalsIgnoreCase(VERSION_V1))
                throw new IllegalArgumentException("unknown API version");
            if ( modelName.isEmpty())
                return listAllModels(request);

            Model model = modelsEvaluator.requireModel(modelName.get());

            Optional<Integer> evalSegment = path.lastIndexOf(EVALUATE);
            String[] function = path.range(3, evalSegment);
            if (evalSegment.isPresent()) {
                return evaluateModel(request, model, function);
            }
            return listModelInformation(request, model, function);

        } catch (IllegalArgumentException e) {
            return new ErrorResponse(404, Exceptions.toMessageString(e));
        } catch (IllegalStateException e) { // On missing bindings
            return new ErrorResponse(400, Exceptions.toMessageString(e));
        }
    }

    private HttpResponse evaluateModel(HttpRequest request, Model model, String[] function)  {
        FunctionEvaluator evaluator = model.evaluatorOf(function);

        property(request, missingValueKey).ifPresent(missingValue -> evaluator.setMissingValue(Tensor.from(missingValue)));

        for (Map.Entry<String, TensorType> argument : evaluator.function().argumentTypes().entrySet()) {
            Optional<String> value = property(request, argument.getKey());
            if (value.isPresent()) {
                try {
                    evaluator.bind(argument.getKey(), Tensor.from(argument.getValue(), value.get()));
                } catch (IllegalArgumentException e) {
                    evaluator.bind(argument.getKey(), value.get());  // since we don't yet support tensors with string values
                }
            }
        }
        Tensor result = evaluator.evaluate();

        Optional<String> format = property(request, "format.tensors");
        if (format.isPresent() && format.get().equalsIgnoreCase("long")) {
            return new Response(200, JsonFormat.encode(result));
        }
        else if (format.isPresent() && format.get().equalsIgnoreCase("string")) {
            return new Response(200, result.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new Response(200, JsonFormat.encodeShortForm(result));
    }

    private HttpResponse listAllModels(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        for (String modelName: modelsEvaluator.models().keySet()) {
            root.setString(modelName, baseUrl(request) + modelName);
        }
        return new Response(200, com.yahoo.slime.JsonFormat.toJsonBytes(slime));
    }

    private HttpResponse listModelInformation(HttpRequest request, Model model, String[] function) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("model", model.name());
        if (function.length == 0) {
            listFunctions(request, model, root);
        } else {
            listFunctionDetails(request, model, function, root);
        }
        return new Response(200, com.yahoo.slime.JsonFormat.toJsonBytes(slime));
    }

    private void listFunctions(HttpRequest request, Model model, Cursor cursor) {
        Cursor functions = cursor.setArray("functions");
        for (ExpressionFunction func : model.functions()) {
            Cursor function = functions.addObject();
            listFunctionDetails(request, model, new String[] { func.getName() }, function);
        }
    }

    private void listFunctionDetails(HttpRequest request, Model model, String[] function, Cursor cursor) {
        String compactedFunction = String.join(".", function);
        FunctionEvaluator evaluator = model.evaluatorOf(function);
        cursor.setString("function", compactedFunction);
        cursor.setString("info", baseUrl(request) + model.name() + "/" + compactedFunction);
        cursor.setString("eval", baseUrl(request) + model.name() + "/" + compactedFunction + "/" + EVALUATE);
        Cursor bindings = cursor.setArray("arguments");
        for (Map.Entry<String, TensorType> argument : evaluator.function().argumentTypes().entrySet()) {
            Cursor binding = bindings.addObject();
            binding.setString("name", argument.getKey());
            binding.setString("type", argument.getValue().toString());
        }
    }

    private Optional<String> property(HttpRequest request, String name) {
        return Optional.ofNullable(request.getProperty(name));
    }

    private String baseUrl(HttpRequest request) {
       URI uri = request.getUri();
       StringBuilder sb = new StringBuilder();
       sb.append(uri.getScheme()).append("://");
       if (request.getHeader("Host") != null) {
           sb.append(request.getHeader("Host"));
       } else {
           sb.append(uri.getHost());
           if (uri.getPort() >= 0) {
               sb.append(":").append(uri.getPort());
           }
       }
       sb.append("/").append(API_ROOT).append("/").append(VERSION_V1).append("/");
       return sb.toString();
    }

    private static class Path {

        private final String[] segments;

        public Path(HttpRequest httpRequest) {
            segments = splitPath(httpRequest);
        }

        Optional<String> segment(int index) {
            return (index < 0 || index >= segments.length) ? Optional.empty() : Optional.of(segments[index]);
        }

        Optional<Integer> lastIndexOf(String segment) {
            for (int i = segments.length - 1; i >= 0; --i) {
                if (segments[i].equalsIgnoreCase(segment)) {
                    return Optional.of(i);
                }
            }
            return Optional.empty();
        }

        public String[] range(int start, Optional<Integer> end) {
            return Arrays.copyOfRange(segments, start, end.isPresent() ? end.get() : segments.length);
        }

        private static String[] splitPath(HttpRequest request) {
            String path = request.getUri().getPath().toLowerCase();
            if (path.startsWith("/")) {
                path = path.substring("/".length());
            }
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return path.split("/");
        }

    }

    private static class Response extends HttpResponse {

        private final byte[] data;

        Response(int code, byte[] data) {
            super(code);
            this.data = data;
        }

        Response(int code, String data) {
            this(code, data.getBytes(Charset.forName(DEFAULT_CHARACTER_ENCODING)));
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write(data);
        }
    }

    private static class ErrorResponse extends Response {
        ErrorResponse(int code, String data) {
            super(code, "{\"error\":\"" + data + "\"}");
        }
    }

}

