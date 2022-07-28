// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.rendering;

import com.yahoo.component.provider.ListenableFreezableClass;
import com.yahoo.container.jdisc.ContentChannelOutputStream;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.response.ArrayDataList;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.processing.response.IncomingData;
import com.yahoo.text.Utf8;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Einar M R Rosenvinge
 */
public class AsynchronousSectionedRendererTest {

    private static final Charset CHARSET = Utf8.getCharset();

    @Test
    void testRenderersOfTheSamePrototypeUseTheSameExecutor() {
        TestRenderer rendererPrototype = new TestRenderer();
        TestRenderer rendererCopy1 = (TestRenderer) rendererPrototype.clone();
        rendererCopy1.init();
        assertSame(rendererPrototype.getRenderingExecutor(), rendererCopy1.getRenderingExecutor());
    }

    @Test
    void testRenderersOfDifferentPrototypesUseDifferentExecutors() {
        TestRenderer rendererPrototype1 = new TestRenderer();
        TestRenderer rendererCopy1 = (TestRenderer) rendererPrototype1.clone();
        rendererCopy1.init();

        TestRenderer rendererPrototype2 = new TestRenderer();
        TestRenderer rendererCopy2 = (TestRenderer) rendererPrototype2.clone();
        rendererCopy2.init();
        assertNotSame(rendererPrototype1.getRenderingExecutor(), rendererCopy2.getRenderingExecutor());
    }

    @Test
    void testAsyncSectionedRenderer() throws IOException, InterruptedException {
        StringDataList dataList = createDataListWithStrangeStrings();

        TestRenderer renderer = new TestRenderer();
        renderer.init();

        String str = render(renderer, dataList);

        assertEquals(" beginResponse beginList[f\\o\"o, [b/a\br, f\f\no\ro\tbar\u0005]] dataf\\o\"o beginList[b/a\br, " +
                "f\f\no\ro\tbar\u0005] datab/a\br dataf\f\no\ro\tbar\u0005 endList[b/a\br, f\f\no\ro\tbar\u0005] endList[f\\o\"o, [b/a\br, f\f\no\ro\tbar\u0005]] endResponse",
                str);
    }

    @Test
    void testEmptyProcessingRendering() throws IOException, InterruptedException {
        Request request = new Request();
        DataList dataList = ArrayDataList.create(request);

        assertEquals("{\"datalist\":[]}", render(dataList));
    }

    @Test
    void testProcessingRendering() throws IOException, InterruptedException {
        StringDataList dataList = createDataListWithStrangeStrings();

        assertEquals("{\"datalist\":[" +
                "{\"data\":\"f\\\\o\\\"o\"}," +
                "{\"datalist\":[" +
                "{\"data\":\"b/a\\br\"}," +
                "{\"data\":\"f\\f\\no\\ro\\tbar\\u0005\"}" +
                "]}" +
                "]}",
                render(dataList));
    }

    @Test
    void testProcessingRenderingWithErrors() throws IOException, InterruptedException {
        StringDataList dataList = createDataList();

        // Add errors
        dataList.request().errors().add(new ErrorMessage("m1", "d1"));
        dataList.request().errors().add(new ErrorMessage("m2", "d2"));

        assertEquals("{\"errors\":[" +
                "\"m1: d1\"," +
                "\"m2: d2\"" +
                "]," +
                "\"datalist\":[" +
                "{\"data\":\"l1\"}," +
                "{\"datalist\":[" +
                "{\"data\":\"l11\"}," +
                "{\"data\":\"l12\"}" +
                "]}" +
                "]}",
                render(dataList));
    }

    @Test
    void testProcessingRenderingWithStackTraces() throws IOException, InterruptedException {
        Exception exception;
        // Create thrown exception
        try {
            throw new RuntimeException("Thrown");
        }
        catch (RuntimeException e) {
            exception = e;
        }

        StringDataList dataList = createDataList();

        // Add errors
        dataList.request().errors().add(new ErrorMessage("m1", "d1", exception));
        dataList.request().errors().add(new ErrorMessage("m2", "d2"));

        assertEquals(
                "{\"errors\":[" +
                        "{" +
                        "\"error\":\"m1: d1: Thrown\"," +
                        "\"stacktrace\":\"java.lang.RuntimeException: Thrown\\n\\tat com.yahoo.processing.rendering.AsynchronousSectionedRendererTest.",
                render(dataList).substring(0, 157));
    }

    @Test
    void testProcessingRenderingWithClonedErrorRequest() throws IOException, InterruptedException {
        StringDataList dataList = createDataList();

        // Add errors
        dataList.request().errors().add(new ErrorMessage("m1", "d1"));
        dataList.request().errors().add(new ErrorMessage("m2", "d2"));
        dataList.add(new StringDataList(dataList.request().clone())); // Cloning a request which contains errors
        // ... should not cause repetition of those errors

        assertEquals("{\"errors\":[" +
                "\"m1: d1\"," +
                "\"m2: d2\"" +
                "]," +
                "\"datalist\":[" +
                "{\"data\":\"l1\"}," +
                "{\"datalist\":[" +
                "{\"data\":\"l11\"}," +
                "{\"data\":\"l12\"}" +
                "]}," +
                "{\"datalist\":[]}" +
                "]}",
                render(dataList));
    }

    @Test
    void testProcessingRenderingWithClonedErrorRequestContainingNewErrors() throws IOException, InterruptedException {
        StringDataList dataList = createDataList();

        // Add errors
        dataList.request().errors().add(new ErrorMessage("m1", "d1"));
        dataList.request().errors().add(new ErrorMessage("m2", "d2"));
        dataList.add(new StringDataList(dataList.request().clone())); // Cloning a request containing errors
        // and adding new errors to it
        dataList.asList().get(2).request().errors().add(new ErrorMessage("m3", "d3"));

        assertEquals("{\"errors\":[" +
                "\"m1: d1\"," +
                "\"m2: d2\"" +
                "]," +
                "\"datalist\":[" +
                "{\"data\":\"l1\"}," +
                "{\"datalist\":[" +
                "{\"data\":\"l11\"}," +
                "{\"data\":\"l12\"}" +
                "]}," +
                "{\"errors\":[" +
                "\"m3: d3\"" +
                "]," +
                "\"datalist\":[]}" +
                "]}",
                render(dataList));
    }

    public StringDataList createDataList() {
        Request request = new Request();
        StringDataList dataList = new StringDataList(request);
        dataList.add(new StringDataItem(request, "l1"));
        StringDataList secondLevel = new StringDataList(request);
        secondLevel.add(new StringDataItem(request, "l11"));
        secondLevel.add(new StringDataItem(request, "l12"));
        dataList.add(secondLevel);
        return dataList;
    }

    public StringDataList createDataListWithStrangeStrings() {
        Request request = new Request();
        StringDataList dataList = new StringDataList(request);
        dataList.add(new StringDataItem(request, "f\\o\"o"));
        StringDataList secondLevel = new StringDataList(request);
        secondLevel.add(new StringDataItem(request, "b/a\br"));
        secondLevel.add(new StringDataItem(request, "f\f\no\ro\tbar\u0005"));
        dataList.add(secondLevel);
        return dataList;
    }

    public String render(DataList data) throws InterruptedException, IOException {
        ProcessingRenderer renderer = new ProcessingRenderer();
        renderer.init();
        return render(renderer, data);
    }

    @SuppressWarnings({"unchecked"})
    public String render(Renderer renderer, DataList data) throws InterruptedException, IOException {
        TestContentChannel contentChannel = new TestContentChannel();

        Execution execution = Execution.createRoot(new NoopProcessor(), 0, null);

        final ContentChannelOutputStream stream = new ContentChannelOutputStream(contentChannel);
        CompletableFuture<Boolean> result = renderer.renderResponse(stream, new Response(data), execution, null);

        int waitCounter = 1000;
        while (!result.isDone()) {
            Thread.sleep(60);
            --waitCounter;
            if (waitCounter < 0) {
                throw new IllegalStateException();
            }
        }

        stream.close();
        contentChannel.close(null);

        String str = "";
        for (ByteBuffer buf : contentChannel.getBuffers()) {
            str += Utf8.toString(buf);
        }
        return str;
    }

    private static class TestRenderer extends AsynchronousSectionedRenderer<Response> {

        private OutputStream stream;

        private StringDataList checkInstanceList(DataList<?> list) {
            if (!(list instanceof StringDataList)) {
                throw new IllegalArgumentException();
            }
            return (StringDataList) list;
        }

        private StringData checkInstanceData(Data data) {
            if (!(data instanceof StringData)) {
                throw new IllegalArgumentException();
            }
            return (StringData) data;
        }


        @Override
        public void beginResponse(OutputStream stream) {
            this.stream = stream;
            try {
                stream.write(" beginResponse".getBytes(CHARSET));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void beginList(DataList<?> list) {
            StringDataList stringDataList = checkInstanceList(list);
            try {
                stream.write((" beginList" + stringDataList.getString()).getBytes(CHARSET));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void data(Data data) {
            StringData stringData = checkInstanceData(data);
            try {
                stream.write((" data" + stringData.getString()).getBytes(CHARSET));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void endList(DataList<?> list) {
            StringDataList stringDataList = checkInstanceList(list);
            try {
                stream.write((" endList" + stringDataList.getString()).getBytes(CHARSET));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void endResponse() {
            try {
                stream.write(" endResponse".getBytes(CHARSET));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getEncoding() {
            return CHARSET.name();
        }

        @Override
        public String getMimeType() {
            return "text/plain";
        }
    }

    private static abstract class StringData extends ListenableFreezableClass implements Data {
        private final Request request;

        private StringData(Request request) {
            this.request = request;
        }

        @Override
        public Request request() {
            return request;
        }

        public abstract String getString();

        @Override
        public String toString() {
            return getString();
        }
    }

    private class StringDataItem extends StringData {

        private final String string;

        private StringDataItem(Request request, String string) {
            super(request);
            this.string = string;
        }

        @Override
        public String getString() {
            return string;
        }
    }

    private class StringDataList extends StringData implements DataList<StringData> {

        private final ArrayList<StringData> list = new ArrayList<>();

        private final IncomingData incomingData;

        private StringDataList(Request request) {
            super(request);
            incomingData = new IncomingData.NullIncomingData<>(this);
        }

        @Override
        public StringData add(StringData data) {
            list.add(data);
            return data;
        }

        @Override
        public StringData get(int index) {
            return list.get(index);
        }

        @Override
        public List<StringData> asList() {
            return list;
        }

        @SuppressWarnings("unchecked")
        @Override
        public IncomingData<StringData> incoming() {
            return incomingData;
        }

        @Override
        public void addDataListener(Runnable runnable) {
            throw new RuntimeException("Not supported");
        }

        @Override
        public CompletableFuture<DataList<StringData>> completeFuture() {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public String getString() {
            return list.toString();
        }
    }

    private static class NoopProcessor extends Processor {

        @Override
        public Response process(Request request, Execution execution) {
            return execution.process(request);
        }

    }

}
