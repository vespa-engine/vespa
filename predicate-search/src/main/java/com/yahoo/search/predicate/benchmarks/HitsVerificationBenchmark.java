// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yahoo.search.predicate.Config;
import com.yahoo.search.predicate.Hit;
import com.yahoo.search.predicate.PredicateIndex;
import com.yahoo.search.predicate.PredicateIndexBuilder;
import com.yahoo.search.predicate.PredicateQuery;
import com.yahoo.search.predicate.serialization.PredicateQuerySerializer;
import com.yahoo.search.predicate.utils.VespaFeedParser;
import com.yahoo.search.predicate.utils.VespaQueryParser;
import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import io.airlift.airline.HelpOption;
import io.airlift.airline.Option;
import io.airlift.airline.SingleCommand;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.yahoo.search.predicate.benchmarks.HitsVerificationBenchmark.BenchmarkArguments.*;
import static java.util.stream.Collectors.joining;

/**
 * A test that runs outputs the hits for each query into result file.
 *
 * @author bjorncs
 */
public class HitsVerificationBenchmark {

    public static void main(String[] rawArgs) throws IOException {
        Optional<BenchmarkArguments> wrappedArgs = getArguments(rawArgs);
        if (!wrappedArgs.isPresent()) return;
        BenchmarkArguments args = wrappedArgs.get();
        Map<String, Object> output = new TreeMap<>();
        addArgsToOutput(output, args);

        Config config = new Config.Builder()
                .setArity(args.arity)
                .setUseConjunctionAlgorithm(args.algorithm == Algorithm.CONJUNCTION)
                .build();

        PredicateIndex index = getIndex(args, config, output);

        Stream<PredicateQuery> queries = parseQueries(args.format, args.queryFile);
        int totalHits = runQueries(index, queries, args.outputFile);
        output.put("Total hits", totalHits);
        writeOutputToStandardOut(output);
    }

    private static PredicateIndex getIndex(BenchmarkArguments args, Config config, Map<String, Object> output) throws IOException {
        if (args.feedFile != null) {
            PredicateIndexBuilder builder = new PredicateIndexBuilder(config);
            AtomicInteger idCounter = new AtomicInteger();
            VespaFeedParser.parseDocuments(
                    args.feedFile, Integer.MAX_VALUE, p -> builder.indexDocument(idCounter.incrementAndGet(), p));
            builder.getStats().putValues(output);
            return builder.build();
        } else {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(args.indexFile)))) {
                long start = System.currentTimeMillis();
                PredicateIndex index = PredicateIndex.fromInputStream(in);
                output.put("Time deserialize index", System.currentTimeMillis() - start);
                return index;
            }
        }
    }

    private static int runQueries(
            PredicateIndex index, Stream<PredicateQuery> queries, String outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false))) {
            AtomicInteger i = new AtomicInteger();
            PredicateIndex.Searcher searcher = index.searcher();
            return queries.map(searcher::search)
                    .peek(hits -> {if (i.get() % 500 == 0) {index.rebuildPostingListCache();}})
                    .mapToInt(hits -> writeHits(i.getAndIncrement(), hits, writer))
                    .sum();

        }
    }

    private static Stream<PredicateQuery> parseQueries(Format format, String queryFile)
            throws IOException {
        PredicateQuerySerializer serializer = new PredicateQuerySerializer();
        return Files.lines(Paths.get(queryFile))
                .map(line ->
                        format == Format.JSON
                                ? serializer.fromJSON(line)
                                : VespaQueryParser.parseQueryFromQueryProperties(line));

    }

    private static int writeHits(int i, Stream<Hit> hitStream, BufferedWriter writer) {
        try {
            List<Hit> hits = hitStream.toList();
            writer.append(Integer.toString(i))
                    .append(": ")
                    .append(hits.stream()
                            .map(hit -> String.format("(%d, 0x%x)", hit.getDocId(), hit.getSubquery()))
                            .collect(joining(", ", "[", "]")))
                    .append("\n\n");
            return hits.size();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<BenchmarkArguments> getArguments(String[] rawArgs) {
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

    private static void addArgsToOutput(Map<String, Object> output, BenchmarkArguments args) {
        output.put("Arity", args.arity);
        output.put("Algorithm", args.algorithm);
        output.put("Query format", args.format);
        output.put("Feed file", args.feedFile);
        output.put("Query file", args.queryFile);
        output.put("Output file", args.outputFile);
        output.put("Index file", args.indexFile);
    }

    private static void writeOutputToStandardOut(Map<String, Object> output) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(System.out, output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Command(name = "hits-verifier",
            description = "Java predicate search system test that outputs the returned hits for each query")
    public static class BenchmarkArguments {

        public enum Format{JSON, VESPA}
        public enum Algorithm{CONJUNCTION, INTERVALONLY}

        @Option(name = {"-a", "--arity"}, description = "Arity")
        public int arity = 2;

        @Option(name = {"-al", "--algorithm"}, description = "Algorithm (CONJUNCTION or INTERVALONLY)")
        public Algorithm algorithm = Algorithm.INTERVALONLY;

        @Option(name = {"-qf", "--query-format"}, description =
                "Query format. Valid formats are either 'vespa' (obsolete query property format) or 'json'.")
        public Format format = Format.VESPA;

        @Option(name = {"-ff", "--feed-file"}, description = "File path to feed file (Vespa XML feed)")
        public String feedFile;

        @Option(name = {"-if", "--index-file"}, description = "File path to index file (Serialized index)")
        public String indexFile;

        @Option(name = {"-quf", "--query-file"}, description = "File path to a query file")
        public String queryFile;

        @Arguments(title = "Output file", description = "File path to output file")
        public String outputFile;

        @Inject
        public HelpOption helpOption;
    }
}
