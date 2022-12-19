// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import com.yahoo.lang.MutableInteger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * @author bjorncs
 */
public class SspTest {

    private final Random rng = new Random(123456);

    @Test
    public void test() throws IOException {
        var setAttributes = new TreeMap<String, SetAttributeDistribution>();
        var rangeAttributes = new TreeMap<String, RangeAttributeDistribution>();
        MutableInteger count = new MutableInteger(0);
//        try (var lines = Files.lines(Paths.get("/Users/bjorncs/code/corp/hgarapati14/ssp-deal-vespa/predicates-original.txt")).limit(1000)) {
        try (var lines = Files.lines(Paths.get("/Users/bjorncs/code/corp/hgarapati14/ssp-deal-vespa/predicates-generated.txt"))) {
            lines.forEach(l -> {
                var predicate = Predicate.fromString(l);
                for (var op : ((Conjunction) predicate).getOperands()) {
                    if (op instanceof FeatureSet fs) {
                        onFeatureSet(setAttributes, fs, false);
                    } else if (op instanceof FeatureRange fr) {
                        onFeatureRange(rangeAttributes, fr, false);
                    } else if (op instanceof Negation neg) {
                        if (neg.getOperand() instanceof FeatureSet fs) {
                            onFeatureSet(setAttributes, fs, true);
                        } else if (neg.getOperand() instanceof FeatureRange fr) {
                            onFeatureRange(rangeAttributes, fr, true);
                        } else {
                            throw new IllegalArgumentException(neg.toString());
                        }
                    } else {
                        throw new IllegalArgumentException(op.toString());
                    }
                }
                if (count.next() % 1000 == 0) System.out.println(count.get());
            });
        }
        System.out.printf("Attribute sets %d, range sets %d%n", setAttributes.size(), rangeAttributes.size());
        generatePredicates(setAttributes, rangeAttributes);
        generateQueries(setAttributes, rangeAttributes);
    }

    private void generateQueries(TreeMap<String, SetAttributeDistribution> sa, TreeMap<String, RangeAttributeDistribution> ra) throws IOException {
        try (var writer = Files.newBufferedWriter(Paths.get("/Users/bjorncs/code/corp/hgarapati14/ssp-deal-vespa/generated-queries.txt"))) {
            for (int i = 0; i < 1000; i++) {

            }
        }
    }

    private void generatePredicates(TreeMap<String, SetAttributeDistribution> sad,
                                           TreeMap<String, RangeAttributeDistribution> rad) throws IOException {
        try (var writer = Files.newBufferedWriter(Paths.get("/Users/bjorncs/code/corp/hgarapati14/ssp-deal-vespa/generated-feed.json"))) {
            for (int i = 0; i < 21000; i++) {
                var conjunction = new Conjunction();
                sad.forEach((name, sa) -> {
                    if (rng.nextDouble() >= 0.9) {
                        conjunction.addOperand(new Negation(createFeatureSet(name, sa, rng)));
                    } else {
                        conjunction.addOperand(createFeatureSet(name, sa, rng));
                    }
                });
                rad.forEach((name, sa) -> {
                    if (rng.nextDouble() >= 0.9) {
                        conjunction.addOperand(new Negation(createFeatureRange(name, rng)));
                    } else {
                        conjunction.addOperand(createFeatureRange(name, rng));
                    }
                });
                if (i % 1000 == 0) System.out.println(i);
                writer.write("{\"fields\":{\"deal_id\":\"deal-%1$d\",\"target\":\"%2$s\"},\"id\":\"id:mynamespace:deal::%1$d\"}%n".formatted(i + 1, conjunction.toString()));
            }
        }
    }

    private void onFeatureRange(TreeMap<String, RangeAttributeDistribution> rangeAttributes, FeatureRange fr, boolean negated) {
        var attribute = fr.getKey();
        var stats = rangeAttributes.computeIfAbsent(attribute, __ -> RangeAttributeDistribution.empty());
        stats.predicates().next();
        var range = Range.of(fr.getFromInclusive(), fr.getToInclusive());
        stats.valueDistribution().merge(range, 1, Integer::sum);
        if (negated) stats.negated().next();
    }

    private void onFeatureSet(TreeMap<String, SetAttributeDistribution> setAttributes, FeatureSet fs, boolean negated) {
        var attribute = fs.getKey();
        var stats = setAttributes.computeIfAbsent(attribute, __ -> SetAttributeDistribution.empty());
        stats.predicates().next();
        stats.lengthDistribution().merge(fs.getValues().size(), 1, Integer::sum);
        for (String v : fs.getValues()) {
            stats.valueDistribution().merge(v, 1, Integer::sum);
        }
        if (negated) stats.negated().next();
    }

    private FeatureSet createFeatureSet(String name, SetAttributeDistribution sa, Random rng) {
        var fs = new FeatureSet(name);
        var values = List.copyOf(sa.valueDistribution().keySet());
        double g1 = Math.min(1, Math.max(0d, rng.nextGaussian(0d, 1)));
        var count = (int)Math.min(25d, Math.max(1d, Math.ceil(g1 * sa.valueDistribution().size())));
        for (int i = 0; i < count; i++) {
            double g2 = Math.min(1d, Math.max(0d, rng.nextGaussian(0, 1)));
            var index = (int)Math.min(values.size()-1d, g2 * values.size());
            fs.addValue(values.get(index));
        }
        return fs;
    }

    private FeatureRange createFeatureRange(String name, Random rng) {
        var fr = new FeatureRange(name);
        double lower = rng.nextDouble();
        var from = lower < 0.1 ? null : Math.round(lower * 100) * 10;
        fr.setFromInclusive(from);
        double upper = rng.nextDouble(Math.min(lower+0.2, 0.99d), 1d);
        var to = upper > 0.9 ? null : Math.round(upper * 100) * 10;
        fr.setToInclusive(to);
        return fr;
    }

    private record SetAttributeDistribution(Map<String, Integer> valueDistribution, Map<Integer, Integer> lengthDistribution, MutableInteger predicates, MutableInteger negated) {
        static SetAttributeDistribution empty() {
            return new SetAttributeDistribution(new TreeMap<>(), new TreeMap<>(), new MutableInteger(0), new MutableInteger(0));
        }
    }

    private record RangeAttributeDistribution(Map<Range, Integer> valueDistribution, MutableInteger predicates, MutableInteger negated) {
        static RangeAttributeDistribution empty() {
            return new RangeAttributeDistribution(new TreeMap<>(), new MutableInteger(0), new MutableInteger(0));
        }
    }

    private record Range(long from, long to) implements Comparable<Range> {

        static Range of(Long from, Long to) { return new Range(from == null ? -1 : from, to == null ? -1 : to); }

        @Override
        public int compareTo(Range o) {
            return Comparator.comparing(Range::to).thenComparingLong(Range::from).compare(this, o);
        }
    }
}
