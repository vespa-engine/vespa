package ai.vespa.models.handler;

import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.JsonFormat;

import java.io.IOException;
import java.io.OutputStream;

public class ModelsEvaluationHandler extends LoggingRequestHandler {

    private final ModelsEvaluator modelsEvaluator;

    public ModelsEvaluationHandler(ModelsEvaluator modelsEvaluator, Context context) {
        super(context);
        this.modelsEvaluator = modelsEvaluator;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Tensor result = modelsEvaluator.evaluatorOf(property("model", "serving_default", request),
                                                    request.getProperty("function"))
                                       .evaluate();
        return new RawResponse(JsonFormat.encode(result));
    }

    private String property(String name, String defaultValue, HttpRequest request) {
        String value = request.getProperty(name);
        if (value == null) return defaultValue;
        return value;
    }

    private static class RawResponse extends HttpResponse {

        private final byte[] data;

        RawResponse(byte[] data) {
            super(200);
            this.data = data;
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

}

