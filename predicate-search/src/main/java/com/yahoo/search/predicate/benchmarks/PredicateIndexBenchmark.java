// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Iterators;
import com.yahoo.search.predicate.Config;
import com.yahoo.search.predicate.PredicateIndex;
import com.yahoo.search.predicate.PredicateIndexBuilder;
import com.yahoo.search.predicate.PredicateQuery;
import com.yahoo.search.predicate.serialization.PredicateQuerySerializer;
import com.yahoo.search.predicate.utils.VespaFeedParser;
import com.yahoo.search.predicate.utils.VespaQueryParser;
import io.airlift.airline.Command;
import io.airlift.airline.HelpOption;
import io.airlift.airline.Option;
import io.airlift.airline.SingleCommand;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.yahoo.search.predicate.benchmarks.PredicateIndexBenchmark.BenchmarkArguments.Algorithm;
import static com.yahoo.search.predicate.benchmarks.PredicateIndexBenchmark.BenchmarkArguments.Format;

/**
 * A benchmark that tests the indexing and search performance.
 *
 * @author bjorncs
 */
public class PredicateIndexBenchmark {

    private static final Map<String, Object> output = new TreeMap<>();

    public static void main(String[] rawArgs) throws IOException {
        Optional<BenchmarkArguments> optionalArgs = getBenchmarkArguments(rawArgs);
        if (!optionalArgs.isPresent()) return;
        BenchmarkArguments args = optionalArgs.get();

        putBenchmarkArgumentsToOutput(args);

        long start = System.currentTimeMillis();
        Config config = new Config.Builder()
                .setArity(args.arity)
                .setUseConjunctionAlgorithm(args.algorithm == Algorithm.CONJUNCTION)
                .build();
        PredicateIndex index = getIndex(args, config);
        if (args.indexOutputFile != null) {
            writeIndexToFile(index, args.indexOutputFile);
        }
        if (args.queryFile != null) {
            runQueries(args, index);
        }
        output.put("Total time", System.currentTimeMillis() - start);
        output.put("Timestamp", new Date().toString());
        writeOutputToStandardOut();
    }

    private static Optional<BenchmarkArguments> getBenchmarkArguments(String[] rawArgs) {
        BenchmarkArguments args = SingleCommand.singleCommand(BenchmarkArguments.class).parse(rawArgs);
        if (args.helpOption.showHelpIfRequested()) {
            return Optional.empty();
        }
        if (args.feedFile == null && args.indexFile == null) {
            System.err.println("Provide either a feed file or index file.");
            return Optional.empty();
        }
        return Optional.of(args);
    }

    private static PredicateIndex getIndex(BenchmarkArguments args, Config config) throws IOException {
        if (args.feedFile != null) {
            PredicateIndexBuilder builder = new PredicateIndexBuilder(config);
            long start = System.currentTimeMillis();
            AtomicInteger idCounter = new AtomicInteger();
            int documentCount = VespaFeedParser.parseDocuments(
                    args.feedFile, args.maxDocuments, p -> builder.indexDocument(idCounter.incrementAndGet(), p));
            output.put("Indexed document count", documentCount);
            output.put("Time indexing documents", System.currentTimeMillis() - start);
            builder.getStats().putValues(output);

            start = System.currentTimeMillis();
            PredicateIndex index = builder.build();
            output.put("Time prepare index", System.currentTimeMillis() - start);
            return index;
        } else {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(args.indexFile)))) {
                long start = System.currentTimeMillis();
                PredicateIndex index = PredicateIndex.fromInputStream(in);
                output.put("Time deserialize index", System.currentTimeMillis() - start);
                return index;
            }
        }
    }

    private static void writeIndexToFile(PredicateIndex index, String indexOutputFile) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexOutputFile)))) {
            long start = System.currentTimeMillis();
            index.writeToOutputStream(out);
            output.put("Time write index", System.currentTimeMillis() - start);
        }
    }

    private static void putBenchmarkArgumentsToOutput(BenchmarkArguments args) {
        output.put("Arity", args.arity);
        output.put("Max documents", args.maxDocuments);
        output.put("Max queries", args.maxQueries);
        output.put("Threads", args.nThreads);
        output.put("Runtime", args.runtime);
        output.put("Algorithm", args.algorithm);
        output.put("Serialized index output file", args.indexOutputFile);
        output.put("Feed file", args.feedFile);
        output.put("Query file", args.queryFile);
        output.put("Index file", args.indexFile);
        output.put("Query format", args.format);
        output.put("Warmup", args.warmup);
    }

    private static void runQueries(BenchmarkArguments args, PredicateIndex index) throws IOException {
        List<PredicateQuery> queries = parseQueries(args.queryFile, args.maxQueries, args.format);
        long warmup1 = warmup(queries, index, args.nThreads, args.warmup / 2);
        output.put("Time warmup before building posting cache", warmup1);
        rebuildPostingListCache(index);
        long warmup2 = warmup(queries, index, args.nThreads, args.warmup / 2);
        output.put("Time warmup after building posting cache", warmup2);
        searchIndex(queries, index, args.nThreads, args.runtime);
    }

    private static void rebuildPostingListCache(PredicateIndex index) {
        long start = System.currentTimeMillis();
        index.rebuildPostingListCache();
        output.put("Time rebuild posting list cache", System.currentTimeMillis() - start);
    }

    private static List<PredicateQuery> parseQueries(String queryFile, int maxQueryCount, Format format) throws IOException {
        long start = System.currentTimeMillis();
        List<PredicateQuery> queries = format == Format.VESPA ?
                VespaQueryParser.parseQueries(queryFile, maxQueryCount) :
                PredicateQuerySerializer.parseQueriesFromFile(queryFile, maxQueryCount);
        output.put("Time parse queries", System.currentTimeMillis() - start);
        output.put("Queries parsed", queries.size());
        return queries;
    }

    private static long warmup(List<PredicateQuery> queries, PredicateIndex index, int nThreads, int warmup) {
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        Random random = new Random(42);
        for (int i = 0; i < nThreads; i++) {
            List<PredicateQuery> shuffledQueries = new ArrayList<>(queries);
            Collections.shuffle(shuffledQueries, random);
            executor.submit(new QueryRunner(shuffledQueries, index.searcher()));
        }
        long start = System.currentTimeMillis();
        waitAndShutdown(warmup, executor);
        return System.currentTimeMillis() - start;
    }

    private static void searchIndex(List<PredicateQuery> queries, PredicateIndex index, int nThreads, int runtime) {
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        Random random = new Random(42);
        List<QueryRunner> runners = new ArrayList<>();
        for (int i = 0; i < nThreads; i++) {
            List<PredicateQuery> shuffledQueries = new ArrayList<>(queries);
            Collections.shuffle(shuffledQueries, random);
            runners.add(new QueryRunner(shuffledQueries, index.searcher()));
        }
        long start = System.currentTimeMillis();
        List<Future<ResultMetrics>> futureResults = runners.stream().map(executor::submit).toList();
        waitAndShutdown(runtime, executor);
        long searchTime = System.currentTimeMillis() - start;
        getResult(futureResults).writeMetrics(output, searchTime);
    }

    private static void waitAndShutdown(int warmup, ExecutorService executor) {
        try {
            Thread.sleep(warmup * 1000);
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static ResultMetrics getResult(List<Future<ResultMetrics>> futureResults) {
        try {
            ResultMetrics combined = futureResults.get(0).get();
            for (int i = 1; i < futureResults.size(); i++) {
                combined.combine(futureResults.get(i).get());
            }
            return combined;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static class QueryRunner implements Callable<ResultMetrics> {
        private final List<PredicateQuery> queries;
        private final PredicateIndex.Searcher searcher;

        public QueryRunner(List<PredicateQuery> queries, PredicateIndex.Searcher seacher) {
            this.queries = queries;
            this.searcher = seacher;
        }

        @Override
        public ResultMetrics call() throws Exception {
            Iterator<PredicateQuery> iterator = Iterators.cycle(queries);
            ResultMetrics result = new ResultMetrics();
            while (!Thread.interrupted()) {
                long start = System.nanoTime();
                long hits = searcher.search(iterator.next()).count();
                double latencyMilliseconds = (System.nanoTime() - start) / 1_000_000d;
                result.registerResult(hits, latencyMilliseconds);
            }
            return result;
        }
    }

    private static void writeOutputToStandardOut() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(System.out, output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Command(name = "benchmark", description = "Java predicate search library benchmark")
    public static class BenchmarkArguments {
        public enum Format{JSON, VESPA}
        public enum Algorithm{CONJUNCTION, INTERVALONLY}

        @Option(name = {"-t", "--threads"}, description = "Number of search threads")
        public int nThreads = 1;

        @Option(name = {"-a", "--arity"}, description = "Arity")
        public int arity = 2;

        @Option(name = {"-r", "--runtime"}, description = "Number of seconds to run queries")
        public int runtime = 30;

        @Option(name = {"-md", "--max-documents"},
                description = "The maximum number of documents to index from feed file")
        public int maxDocuments = Integer.MAX_VALUE;

        @Option(name = {"-mq", "--max-queries"}, description = "The maximum number of queries to run from query file")
        public int maxQueries = Integer.MAX_VALUE;

        @Option(name = {"-al", "--algorithm"}, description = "Algorithm (CONJUNCTION or INTERVALONLY)")
        public Algorithm algorithm = Algorithm.INTERVALONLY;

        @Option(name = {"-w", "--warmup"}, description = "Warmup in seconds.")
        public int warmup = 30;

        @Option(name = {"-qf", "--query-format"},
        description = "Query format. Valid formats are either 'VESPA' (obsolete query property format) or 'JSON'.")
        public Format format = Format.VESPA;

        @Option(name = {"-ff", "--feed-file"}, description = "File path to feed file (Vespa XML feed)")
        public String feedFile;

        @Option(name = {"-if", "--index-file"}, description = "File path to index file (Serialized index)")
        public String indexFile;

        @Option(name = {"-wi", "--write-index"}, description = "Serialize index to the given file")
        public String indexOutputFile;

        @Option(name = {"-quf", "--query-file"}, description = "File path to a query file")
        public String queryFile;

        @Inject
        public HelpOption helpOption;
    }
}
