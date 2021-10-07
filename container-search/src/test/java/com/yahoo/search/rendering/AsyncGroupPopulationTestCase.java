// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;
import com.yahoo.concurrent.Receiver;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.processing.response.DefaultIncomingData;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.text.Utf8;

/**
 * Test adding hits to a hit group during rendering.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class AsyncGroupPopulationTestCase {
    private static class WrappedFuture<F> implements ListenableFuture<F> {
        Receiver<Boolean> isListening = new Receiver<>();

        private ListenableFuture<F> wrapped;

        WrappedFuture(ListenableFuture<F> wrapped) {
            this.wrapped = wrapped;
        }

        public void addListener(Runnable listener, Executor executor) {
            wrapped.addListener(listener, executor);
            isListening.put(Boolean.TRUE);
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return wrapped.cancel(mayInterruptIfRunning);
        }

        public boolean isCancelled() {
            return wrapped.isCancelled();
        }

        public boolean isDone() {
            return wrapped.isDone();
        }

        public F get() throws InterruptedException, ExecutionException {
            return wrapped.get();
        }

        public F get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException {
            return wrapped.get(timeout, unit);
        }
    }

    private static class ObservableIncoming<DATATYPE extends Data> extends DefaultIncomingData<DATATYPE> {
        WrappedFuture<DataList<DATATYPE>> waitForIt = null;
        private final Object lock = new Object();

        @Override
        public ListenableFuture<DataList<DATATYPE>> completed() {
            synchronized (lock) {
                if (waitForIt == null) {
                    waitForIt = new WrappedFuture<>(super.completed());
                }
            }
            return waitForIt;
        }
    }

    private static class InstrumentedGroup extends HitGroup {
        private static final long serialVersionUID = 4585896586414935558L;

        InstrumentedGroup(String id) {
            super(id, new Relevance(1), new ObservableIncoming<Hit>());
            ((ObservableIncoming<Hit>) incoming()).assignOwner(this);
        }

    }

    @Test
    public final void test() throws InterruptedException, ExecutionException,
            JsonParseException, JsonMappingException, IOException {
        String rawExpected = "{"
                + "    \"root\": {"
                + "        \"children\": ["
                + "            {"
                + "                \"id\": \"yahoo1\","
                + "                \"relevance\": 1.0"
                + "            },"
                + "            {"
                + "                \"id\": \"yahoo2\","
                + "                \"relevance\": 1.0"
                + "            }"
                + "        ],"
                + "        \"fields\": {"
                + "            \"totalCount\": 0"
                + "        },"
                + "        \"id\": \"yahoo\","
                + "        \"relevance\": 1.0"
                + "    }"
                + "}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HitGroup h = new InstrumentedGroup("yahoo");
        h.incoming().add(new Hit("yahoo1"));
        JsonRenderer renderer = new JsonRenderer();
        Result result = new Result(new Query(), h);
        renderer.init();
        ListenableFuture<Boolean> f = renderer.render(out, result,
                new Execution(Execution.Context.createContextStub()),
                result.getQuery());
        WrappedFuture<DataList<Hit>> x = (WrappedFuture<DataList<Hit>>) h.incoming().completed();
        x.isListening.get(86_400_000);
        h.incoming().add(new Hit("yahoo2"));
        h.incoming().markComplete();
        Boolean b = f.get();
        assertTrue(b);
        String rawGot = Utf8.toString(out.toByteArray());
        ObjectMapper m = new ObjectMapper();
        Map<?, ?> expected = m.readValue(rawExpected, Map.class);
        Map<?, ?> got = m.readValue(rawGot, Map.class);
        assertEquals(expected, got);
    }

}
