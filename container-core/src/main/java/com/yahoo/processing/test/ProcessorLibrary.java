// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.AsyncExecution;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.execution.ExecutionWithResponse;
import com.yahoo.processing.execution.RunnableExecution;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.response.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * A collection of processors for test purposes.
 *
 * @author bratseth
 */
public class ProcessorLibrary {

    private ProcessorLibrary() {
    }

    // ---------------------------------------- Data types

    public static class StringData extends AbstractData {

        private String string;

        public StringData(Request request, String string) {
            super(request);
            this.string = string;
        }

        public void setString(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }

    }

    public static class MapData extends AbstractData {

        private Map map = new LinkedHashMap();

        public MapData(Request request) {
            super(request);
        }

        public Map map() { return map; }

        @Override
        public String toString() {
            return "map data: " + map;
        }

    }

    // ---------------------------------------- DataLists

    public static class UnorderedArrayDataList extends ArrayDataList implements Ordered {

        public UnorderedArrayDataList(Request request) {
            super(request);
        }

        @Override
        public boolean isOrdered() {return false; }

    }

    // ---------------------------------------- Processors

    /**
     * Makes some modifications to the request, passes it on and finally removes one data item from the response
     */
    public static class CombineData extends Processor {

        public Response process(Request request, Execution execution) {
            request.properties().set("appendage", request.properties().getInteger("appendage") + 1);
            Response response = execution.process(request);

            // Modify the response
            StringData first = (StringData) response.data().get(0);
            StringData third = (StringData) response.data().get(2);
            first.setString(first.toString() + ", " + third.toString());
            response.data().asList().remove(2);
            return response;
        }

    }

    /**
     * Sends the request multiple times to get at least 6 pieces of data
     */
    public static class Get6DataItems extends Processor {

        @SuppressWarnings("unchecked")
        public Response process(Request request, Execution execution) {
            Response response = execution.process(request);
            while (response.data().asList().size() < 6) {
                request.properties().set("appendage", request.properties().getInteger("appendage") + 1);
                Response additional = execution.process(request);
                response.mergeWith(additional);
                response.data().asList().addAll(additional.data().asList());
            }
            return response;
        }

    }

    /**
     * Produces 3 pieces of string data
     */
    public static class DataSource extends Processor {

        @SuppressWarnings("unchecked")
        public Response process(Request request, Execution execution) {
            Response response = execution.process(request);
            response.data().add(new StringData(request, "first." + request.properties().get("appendage")));
            response.data().add(new StringData(request, "second." + request.properties().get("appendage")));
            response.data().add(new StringData(request, "third." + request.properties().get("appendage")));
            return response;
        }

    }

    public static class Federator extends Processor {

        private final List<Chain<? extends Processor>> chains;

        private final boolean ordered;

        /**
         * Federates over the given chains. Returns an ordered response.
         */
        @SafeVarargs
        public Federator(Chain<? extends Processor>... chains) {
            this(true, chains);
        }

        /**
         * Federates over the given chains
         *
         * @param ordered true if the returned list should be ordered (default), false if it should be permissible
         *                to render the datalist from each federated source in the order it completes.
         */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public Federator(boolean ordered, Chain<? extends Processor>... chains) {
            this.chains = Arrays.asList(chains);
            this.ordered = ordered;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Response process(Request request, Execution execution) {
            Response response = ordered ? new Response(request) : new Response(new UnorderedArrayDataList(request));

            List<FutureResponse> futureResponses = new ArrayList<>(chains.size());
            for (Chain<? extends Processor> chain : chains) {
                futureResponses.add(new AsyncExecution(chain, execution).process(request.clone()));
            }
            AsyncExecution.waitForAll(futureResponses, 1000);
            for (FutureResponse futureResponse : futureResponses) {
                Response federatedResponse = futureResponse.get();
                response.data().add(federatedResponse.data());
                response.mergeWith(federatedResponse);
            }
            return response;
        }
    }

    /**
     * A federator which supports returning frozen data from each chain before the response is returned.
     */
    public static class EagerReturnFederator extends Processor {

        private final List<Chain<? extends Processor>> chains;

        private final boolean ordered;

        /**
         * Federates over the given chains. Returns an ordered response.
         */
        @SafeVarargs
        public EagerReturnFederator(Chain<? extends Processor>... chains) {
            this(true, chains);
        }

        /**
         * Federates over the given chains
         *
         * @param ordered true if the returned list should be ordered (default), false if it should be permissible
         *                to render the datalist from each federated source in the order it completes.
         */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public EagerReturnFederator(boolean ordered, Chain<? extends Processor>... chains) {
            this.chains = Arrays.asList(chains);
            this.ordered = ordered;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Response process(Request request, Execution execution) {
            List<FutureResponse> futureResponses = new ArrayList<>(chains.size());
            for (Chain<? extends Processor> chain : chains) {
                futureResponses.add(new AsyncExecution(chain, execution).process(request.clone()));
            }
            AsyncExecution.waitForAll(futureResponses, 1000);
            Response response = ordered ? new Response(request) : new Response(new UnorderedArrayDataList(request));
            for (FutureResponse futureResponse : futureResponses) {
                Response federatedResponse = futureResponse.get();
                response.data().add(federatedResponse.data());
                response.mergeWith(federatedResponse);
            }
            return response;
        }
    }

    /**
     * Adds a data element containing the (recursive) count of concrete (non-list) data elements in the response
     */
    public static class DataCounter extends Processor {

        private String prefix = "";

        public DataCounter() {
        }

        /**
         * The prefix "[name] " is prepended to the string data
         */
        public DataCounter(String name) {
            prefix = "[" + name + "] ";
        }

        @SuppressWarnings("unchecked")
        @Override
        public Response process(Request request, Execution execution) {
            Response response = execution.process(request);
            int dataCount = countData(response.data());
            response.data().add(new StringData(request, prefix + "Data count: " + dataCount));
            return response;
        }

        private int countData(DataList<? extends Data> dataList) {
            int count = 0;
            for (Data data : dataList.asList()) {
                if (data instanceof DataList)
                    count += countData((DataList<?>) data);
                else
                    count++;
            }
            return count;
        }
    }

    // TODO: Replace by below?
    public static class FutureDataSource extends Processor {

        /** The list of incoming data this has created */
        public final List<IncomingData> incomingData = new ArrayList<>();

        @Override
        public Response process(Request request, Execution execution) {
            ArrayDataList dataList = ArrayDataList.createAsync(request);
            incomingData.add(dataList.incoming());
            return new Response(dataList);
        }

    }

    /** Allows waiting for that request to happen. */
    public static class ListenableFutureDataSource extends Processor {

        private final boolean ordered, streamed;

        /** The incoming data this has created */
        public final CompletableFuture<IncomingData> incomingData = new CompletableFuture<>();

        /** Create an instance which returns ordered, streamable data */
        public ListenableFutureDataSource() { this(true, true); }

        public ListenableFutureDataSource(boolean ordered, boolean streamed) {
            this.ordered = ordered;
            this.streamed = streamed;
        }

        @Override
        public Response process(Request request, Execution execution) {
            ArrayDataList dataList;
            if (! ordered)
                dataList = ArrayDataList.createAsyncUnordered(request);
            else if (! streamed)
                dataList = ArrayDataList.createAsyncNonstreamed(request);
            else
                dataList = ArrayDataList.createAsync(request);
            incomingData.complete(dataList.incoming());
            return new Response(dataList);
        }

    }

    /** Allows waiting for that request to happen. */
    public static class RequestCounter extends Processor {

        /** The incoming data this has created */
        public final CompletableFuture<IncomingData> incomingData = new CompletableFuture<>();

        @Override
        public Response process(Request request, Execution execution) {
            ArrayDataList dataList = ArrayDataList.createAsync(request);
            incomingData.complete(dataList.incoming());
            return new Response(dataList);
        }

    }

    /**
     * Multiples the amount of data returned by parallelism by performing parallel executions of the rest of the chain
     */
    public static class BlockingSplitter extends Processor {

        private final int parallelism;

        public BlockingSplitter(int parallelism) {
            this.parallelism = parallelism;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Response process(Request request, Execution execution) {
            try {
                // start executions in other threads
                List<FutureResponse> futures = new ArrayList<>(parallelism - 1);
                for (int i = 1; i < parallelism; i++) {
                    futures.add(new AsyncExecution(execution).process(request.clone()));
                }

                // complete this execution
                Response response = execution.process(request);

                // wait for other executions and merge the responses
                for (Response additionalResponse : AsyncExecution.waitForAll(futures, 1000)) {
                    additionalResponse.data().completeFuture().get(); // block until we have all the data elements
                    for (Object item : additionalResponse.data().asList())
                        response.data().add((Data) item);
                    response.mergeWith(additionalResponse);
                }
                return response;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

    }

    /**
     * Registers an async processing of the chain given in the constructor on completion of the data in the response
     */
    public static class AsyncDataProcessingInitiator extends Processor {

        private final Chain<Processor> asyncChain;

        public AsyncDataProcessingInitiator(Chain<Processor> asyncChain) {
            this.asyncChain = asyncChain;
        }

        @Override
        public Response process(Request request, Execution execution) {
            Response response = execution.process(request);
            // TODO: Consider for to best provide helpers for this
            response.data().completeFuture().whenComplete(
                    (__, ___) ->
                            new RunnableExecution(request, new ExecutionWithResponse(asyncChain, response, execution))
                                    .run());
            return response;
        }

    }

    /**
     * Registers a chain to be invoked each time new data becomes available in the first child list
     */
    public static class StreamProcessingInitiator extends Processor {

        private final Chain<Processor> streamChain;

        public StreamProcessingInitiator(Chain<Processor> streamChain) {
            this.streamChain = streamChain;
        }

        @Override
        public Response process(Request request, Execution execution) {
            Response response = execution.process(request);
            // TODO: Consider for to best provide helpers for this
            response.data().addDataListener(new RunnableExecution(request,
                                                                  new ExecutionWithResponse(streamChain, response, execution)));
            return response;
        }

    }

    /**
     * A processor which on invocation prints the string given on construction
     */
    public static class Echo extends Processor {

        private String s;

        public Echo(String s) {
            this.s = s;
        }

        @Override
        public Response process(Request request, Execution execution) {
            System.out.println(s);
            return execution.process(request);
        }

    }

    /**
     * A processor which adds a StringData item containing the string given in the constructor to every response
     */
    public static class StringDataAdder extends Processor {

        private String string;

        public StringDataAdder(String string) {
            this.string = string;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Response process(Request request, Execution execution) {
            Response response = execution.process(request);
            response.data().add(new StringData(request, string));
            return response;
        }

    }

    /**
     * A processor which adds an ErrorMessage to the request of the top level
     * data of each returned response.
     */
    public static class ErrorAdder extends Processor {

        private ErrorMessage errorMessage;

        public ErrorAdder(ErrorMessage errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public Response process(Request request, Execution execution) {
            Response response = execution.process(request);
            response.data().request().errors().add(errorMessage);
            return response;
        }

    }

    /**
     * A processor which adds a List of StringData items containing the strings given in the constructor to every response
     */
    public static class StringDataListAdder extends Processor {

        private String[] strings;

        public StringDataListAdder(String... strings) {
            this.strings = strings;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Response process(Request request, Execution execution) {
            Response response = execution.process(request);
            DataList<StringData> list = ArrayDataList.create(request);
            for (String string : strings)
                list.add(new StringData(request, string));
            response.data().add(list);
            return response;
        }

    }

    /**
     * Adds a the given trace message at the given trace level
     */
    public static class Trace extends Processor {

        private String traceMessage;
        private int traceLevel;

        public Trace(String traceMessage, int traceLevel) {
            this.traceMessage = traceMessage;
            this.traceLevel = traceLevel;
        }

        @Override
        public Response process(Request request, Execution execution) {
            execution.trace().trace(traceMessage, traceLevel);
            return execution.process(request);
        }

    }

    public static final class StatusSetter extends Processor {

        private final int status;

        public StatusSetter(int status) {
            this.status = status;
        }

        @Override
        public com.yahoo.processing.Response process(com.yahoo.processing.Request request, Execution execution) {
            request.errors().add(new ErrorMessage(status, ""));
            return execution.process(request);
        }

    }

    /**
     * Adds (key, value) to the log value trace.
     */
    public static class LogValueAdder extends Processor {
        private final String key;
        private final String value;

        public LogValueAdder(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Response process(Request request, Execution execution) {
            execution.trace().logValue(key, value);
            return execution.process(request);
        }
    }
}
