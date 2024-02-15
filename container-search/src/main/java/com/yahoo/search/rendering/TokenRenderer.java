package com.yahoo.search.rendering;

import ai.vespa.search.llm.TokenStream;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static com.fasterxml.jackson.databind.SerializationFeature.FLUSH_AFTER_WRITE_VALUE;

/**
 *
 * A comment about SSE
 *
 * @author lesters
 */
public class TokenRenderer extends AsynchronousSectionedRenderer<Result> {

    private static final Logger log = Logger.getLogger(TokenRenderer.class.getName());

    private static final JsonFactory generatorFactory = createGeneratorFactory();
    private volatile JsonGenerator generator;

    private static JsonFactory createGeneratorFactory() {
        var factory = new JsonFactoryBuilder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                .build();
        factory.setCodec(new ObjectMapper(factory).disable(FLUSH_AFTER_WRITE_VALUE));
        return factory;
    }

    private static final boolean RENDER_TOKEN_EVENT_HEADER = true;
    private static final boolean RENDER_END_EVENT = true;

    public TokenRenderer() {
        this(null);
    }

    public TokenRenderer(Executor executor) {
        super(executor);
    }

    @Override
    public void beginResponse(OutputStream outputStream) throws IOException {
        generator = generatorFactory.createGenerator(outputStream, JsonEncoding.UTF8);
        generator.setRootValueSeparator(new SerializedString(""));
    }

    @Override
    public void beginList(DataList<?> dataList) throws IOException {
        if ( ! (dataList instanceof TokenStream)) {
            throw new IllegalArgumentException("TokenRenderer currently only supports TokenStreams");
            // Todo: support results and timing and trace by delegating to JsonRenderer
        }
    }

    @Override
    public void data(Data data) throws IOException {
        if (data instanceof TokenStream.Token token) {
            if (RENDER_TOKEN_EVENT_HEADER) {
                generator.writeRaw("event: token\n");
            }
            generator.writeRaw("data: ");
            generator.writeStartObject();
            generator.writeStringField("token", token.toString());
            generator.writeEndObject();
            generator.writeRaw("\n\n");
            generator.flush();
        }
        else if (data instanceof ErrorHit) {
            for (ErrorMessage error : ((ErrorHit) data).errors()) {
                generator.writeRaw("event: error\n");
                generator.writeRaw("data: ");
                generator.writeStartObject();
                generator.writeStringField("source", error.getSource());
                generator.writeNumberField("error", error.getCode());
                generator.writeStringField("message", error.getMessage());
                generator.writeEndObject();
                generator.writeRaw("\n\n");
                generator.flush();
            }
        }
    }

    @Override
    public void endList(DataList<?> dataList) throws IOException {
    }

    @Override
    public void endResponse() throws IOException {
        if (RENDER_END_EVENT) {
            generator.writeRaw("event: end\n");
        }
        generator.close();
    }

    @Override
    public String getEncoding() {
        return "utf-8";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

}
