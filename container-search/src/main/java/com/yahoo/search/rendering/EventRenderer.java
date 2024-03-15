// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.search.result.EventStream;
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

import static com.fasterxml.jackson.databind.SerializationFeature.FLUSH_AFTER_WRITE_VALUE;

/**
 * A Server-Sent Events (SSE) renderer for asynchronous events such as
 * tokens from a language model.
 *
 * @author lesters
 */
public class EventRenderer extends AsynchronousSectionedRenderer<Result> {

    private static final JsonFactory generatorFactory = createGeneratorFactory();
    private volatile JsonGenerator generator;

    private static JsonFactory createGeneratorFactory() {
        var factory = new JsonFactoryBuilder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                .build();
        factory.setCodec(new ObjectMapper(factory).disable(FLUSH_AFTER_WRITE_VALUE));
        return factory;
    }

    private static final boolean RENDER_EVENT_HEADER = true;
    private static final boolean RENDER_END_EVENT = true;

    public EventRenderer() {
        this(null);
    }

    public EventRenderer(Executor executor) {
        super(executor);
    }

    @Override
    public void beginResponse(OutputStream outputStream) throws IOException {
        generator = generatorFactory.createGenerator(outputStream, JsonEncoding.UTF8);
        generator.setRootValueSeparator(new SerializedString(""));
    }

    @Override
    public void beginList(DataList<?> dataList) throws IOException {
    }

    @Override
    public void data(Data data) throws IOException {
        if (data instanceof EventStream.Event event) {
            if (RENDER_EVENT_HEADER) {
                generator.writeRaw("event: " + event.type() + "\n");
            }
            generator.writeRaw("data: ");
            generator.writeStartObject();
            generator.writeStringField(event.type(), event.toString());
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
        // Todo: support other types of data such as search results (hits), timing and trace
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
        return "text/event-stream";
    }

}
