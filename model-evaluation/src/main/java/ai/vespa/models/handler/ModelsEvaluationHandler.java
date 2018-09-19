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
import com.yahoo.tensor.serialization.JsonFormat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.Executor;

public class ModelsEvaluationHandler extends ThreadedHttpRequestHandler {

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
            if ( ! apiName.isPresent() || ! apiName.get().equalsIgnoreCase(API_ROOT)) {
                throw new IllegalArgumentException("unknown API");
            }
            if ( ! version.isPresent() || ! version.get().equalsIgnoreCase(VERSION_V1)) {
                throw new IllegalArgumentException("unknown API version");
            }
            if ( ! modelName.isPresent()) {
                return listAllModels(request);
            }
            if ( ! modelsEvaluator.models().containsKey(modelName.get())) {
                throw new IllegalArgumentException("no model with name '" + modelName.get() + "' found");
            }

            Model model = modelsEvaluator.models().get(modelName.get());

            // The following logic follows from the spec, in that signature and
            // output are optional if the model only has a single function.

            if (path.segments() == 3) {
                if (model.functions().size() > 1) {
                    return listModelDetails(request, modelName.get());
                }
                return listTypeDetails(request, modelName.get());
            }

            if (path.segments() == 4) {
                if ( ! path.segment(3).get().equalsIgnoreCase(EVALUATE)) {
                    return listTypeDetails(request, modelName.get(), path.segment(3).get());
                }
                if (model.functions().stream().anyMatch(f -> f.getName().equalsIgnoreCase(EVALUATE))) {
                    return listTypeDetails(request, modelName.get(), path.segment(3).get());  // model has a function "eval"
                }
                if (model.functions().size() <= 1) {
                    return evaluateModel(request, modelName.get());
                }
                throw new IllegalArgumentException("attempt to evaluate model without specifying function");
            }

            if (path.segments() == 5) {
                if (path.segment(4).get().equalsIgnoreCase(EVALUATE)) {
                    return evaluateModel(request, modelName.get(), path.segment(3).get());
                }
            }

        } catch (IllegalArgumentException e) {
            return new ErrorResponse(404, e.getMessage());
        }

        return new ErrorResponse(404, "unrecognized request");
    }

    private HttpResponse listAllModels(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        for (String modelName: modelsEvaluator.models().keySet()) {
            root.setString(modelName, baseUrl(request) + modelName);
        }
        return new Response(200, com.yahoo.slime.JsonFormat.toJsonBytes(slime));
    }

    private HttpResponse listModelDetails(HttpRequest request, String modelName) {
        Model model = modelsEvaluator.models().get(modelName);
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        for (ExpressionFunction func : model.functions()) {
            root.setString(func.getName(), baseUrl(request) + modelName + "/" + func.getName());
        }
        return new Response(200, com.yahoo.slime.JsonFormat.toJsonBytes(slime));
    }

    private HttpResponse listTypeDetails(HttpRequest request, String modelName) {
        return listTypeDetails(request, modelsEvaluator.evaluatorOf(modelName));
    }

    private HttpResponse listTypeDetails(HttpRequest request, String modelName, String signatureAndOutput) {
        return listTypeDetails(request, modelsEvaluator.evaluatorOf(modelName, signatureAndOutput));
    }

    private HttpResponse listTypeDetails(HttpRequest request, FunctionEvaluator evaluator) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor bindings = root.setArray("bindings");
        for (String bindingName : evaluator.context().names()) {
            // TODO: Use an API which exposes only the external binding names instead of this
            if (bindingName.startsWith("constant(")) {
                continue;
            }
            if (bindingName.startsWith("rankingExpression(")) {
                continue;
            }
            Cursor binding = bindings.addObject();
            binding.setString("name", bindingName);
            binding.setString("type", "");  // TODO: implement type information when available
        }
        return new Response(200, com.yahoo.slime.JsonFormat.toJsonBytes(slime));
    }

    private HttpResponse evaluateModel(HttpRequest request, String modelName)  {
        return evaluateModel(request, modelsEvaluator.evaluatorOf(modelName));
    }

    private HttpResponse evaluateModel(HttpRequest request, String modelName, String signatureAndOutput)  {
        return evaluateModel(request, modelsEvaluator.evaluatorOf(modelName, signatureAndOutput));
    }

    private HttpResponse evaluateModel(HttpRequest request, FunctionEvaluator evaluator)  {
        for (String bindingName : evaluator.context().names()) {
            property(request, bindingName).ifPresent(s -> evaluator.bind(bindingName, Tensor.from(s)));
        }
        Tensor result = evaluator.evaluate();
        return new Response(200, JsonFormat.encode(result));
    }

    private Optional<String> property(HttpRequest request, String name) {
        return Optional.ofNullable(request.getProperty(name));
    }

    private String baseUrl(HttpRequest request) {
       URI uri = request.getUri();
       StringBuilder sb = new StringBuilder();
       sb.append(uri.getScheme()).append("://").append(uri.getHost());
       if (uri.getPort() >= 0) {
           sb.append(":").append(uri.getPort());
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

        int segments() {
            return segments.length;
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

