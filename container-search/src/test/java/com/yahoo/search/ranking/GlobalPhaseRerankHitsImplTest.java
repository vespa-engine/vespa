// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Type;
import com.yahoo.data.access.helpers.MatchFeatureData;
import com.yahoo.data.access.simple.Value;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.tensor.Tensor;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class GlobalPhaseRerankHitsImplTest {
    static class EvalSum implements Evaluator {
        double baseValue;
        List<Tensor> values = new ArrayList<>();
        EvalSum(double baseValue) { this.baseValue = baseValue; }
        @Override public Evaluator bind(String name, Tensor value) {
            values.add(value);
            return this;
        }
        @Override public double evaluateScore() {
            double result = baseValue;
            for (var value: values) {
                result += value.asDouble();
            }
            return result;
        }
    }
    static FunEvalSpec makeConstSpec(double constValue) {
        return new FunEvalSpec(() -> new EvalSum(constValue), Collections.emptyList(), Collections.emptyList());
    }
    static FunEvalSpec makeSumSpec(List<String> fromQuery, List<String> fromMF) {
        return new FunEvalSpec(() -> new EvalSum(0.0), fromQuery, fromMF);
    }
    static class ExpectingNormalizer extends Normalizer {
        List<Double> expected;
        ExpectingNormalizer(List<Double> expected) {
            super(100);
            this.expected = expected;
        }
        @Override void normalize() {
            double rank = 1;
            assertEquals(size, expected.size());
            for (int i = 0; i < size; i++) {
                assertEquals(data[i], expected.get(i));
                data[i] = rank;
                rank += 1;
            }
        }
        @Override String normalizing() { return "expecting"; }
    }
    static NormalizerSetup makeNormalizer(String name, List<Double> expected, FunEvalSpec evalSpec) {
        return new NormalizerSetup(name, () -> new ExpectingNormalizer(expected), evalSpec);
    }
    static GlobalPhaseSetup makeFullSetup(FunEvalSpec mainSpec, int rerankCount,
                                          List<String> hiddenMF, List<NormalizerSetup> normalizers)
    {
        return new GlobalPhaseSetup(mainSpec, rerankCount, hiddenMF, normalizers);
    }
    static GlobalPhaseSetup makeSimpleSetup(FunEvalSpec mainSpec, int rerankCount) {
        return makeFullSetup(mainSpec, rerankCount, Collections.emptyList(), Collections.emptyList());
    }
    static GlobalPhaseSetup makeNormSetup(FunEvalSpec mainSpec, List<NormalizerSetup> normalizers) {
        return makeFullSetup(mainSpec, 100, Collections.emptyList(), normalizers);
    }
    static record NamedValue(String name, double value) {}
    NamedValue value(String name, double value) {
        return new NamedValue(name, value);
    }
    Query makeQuery(List<NamedValue> inQuery, boolean withPrepare) {
        var query = new Query();
        for (var v: inQuery) {
            query.getRanking().getFeatures().put(v.name, v.value);
        }
        if (withPrepare) {
            query.getRanking().prepare();
        }
        return query;
    }
    Query makeQuery(List<NamedValue> inQuery) { return makeQuery(inQuery, false); }
    Query makeQueryWithPrepare(List<NamedValue> inQuery) { return makeQuery(inQuery, true); }

    static Hit makeHit(String id, double score, FeatureData mf) {
        Hit hit = new Hit(id, score);
        hit.setField("matchfeatures", mf);
        return hit;
    }
    static Hit hit(String id, double score) {
        return makeHit(id, score, FeatureData.empty());
    }
    static class HitFactory {
        MatchFeatureData mfData;
        Map<String,Integer> map = new HashMap<>();
        HitFactory(List<String> mfNames) {
            int i = 0;
            for (var name: mfNames) {
                map.put(name, i++);
            }
            mfData = new MatchFeatureData(mfNames);
        }
        Hit create(String id, double score, List<NamedValue> inMF) {
            var mf = mfData.addHit();
            for (var v: inMF) {
                var idx = map.get(v.name);
                assertNotNull(idx);
                mf.set(idx, v.value);
            }
            return makeHit(id, score, new FeatureData(mf));
        }
    }
    Result makeResult(Query query, List<Hit> hits) {
        var result = new Result(query);
        result.hits().addAll(hits);
        return result;
    }
    static class Expect {
        Map<String,Double> map = new HashMap<>();
        static Expect make(List<Hit> hits) {
            var result = new Expect();
            for (var hit : hits) {
                result.map.put(hit.getId().stringValue(), hit.getRelevance().getScore());
            }
            return result;
        }
        void verifyScores(Result actual) {
            double prev = Double.MAX_VALUE;
            assertEquals(actual.hits().size(), map.size());
            for (var hit : actual.hits()) {
                var name = hit.getId().stringValue();
                var score = map.get(name);
                assertNotNull(score, name);
                assertEquals(score.doubleValue(), hit.getRelevance().getScore(), name);
                assertTrue(score <= prev);
                prev = score;
            }
        }
    }
    void verifyHasMF(Result result, String name) {
        for (var hit: result.hits()) {
            if (hit.getField("matchfeatures") instanceof FeatureData mf) {
                assertNotNull(mf.getTensor(name));
            } else {
                fail("matchfeatures are missing");
            }
        }
    }
    void verifyDoesNotHaveMF(Result result, String name) {
        for (var hit: result.hits()) {
            if (hit.getField("matchfeatures") instanceof FeatureData mf) {
                assertNull(mf.getTensor(name));
            } else {
                fail("matchfeatures are missing");
            }
        }
    }
    void verifyDoesNotHaveMatchFeaturesField(Result result) {
        for (var hit: result.hits()) {
            assertNull(hit.getField("matchfeatures"));
        }
    }
    @Test void partialRerankWithRescaling() {
        var setup = makeSimpleSetup(makeConstSpec(3.0), 2);
        var query = makeQuery(Collections.emptyList());
        var result = makeResult(query, List.of(hit("a", 3), hit("b", 4), hit("c", 5), hit("d", 6)));
        var expect = Expect.make(List.of(hit("a", 1), hit("b", 2), hit("c", 3), hit("d", 3)));
        GlobalPhaseRanker.rerankHitsImpl(setup, query, result);
        expect.verifyScores(result);
    }
    @Test void matchFeaturesCanBePartiallyHidden() {
        var setup = makeFullSetup(makeSumSpec(Collections.emptyList(), List.of("public_value", "private_value")), 2,
                List.of("private_value"), Collections.emptyList());
        var query = makeQuery(Collections.emptyList());
        var factory = new HitFactory(List.of("public_value", "private_value"));
        var result = makeResult(query, List.of(factory.create("a", 1, List.of(value("public_value", 2), value("private_value", 3))),
                factory.create("b", 2, List.of(value("public_value", 5), value("private_value", 7)))));
        var expect = Expect.make(List.of(hit("a", 5), hit("b", 12)));
        GlobalPhaseRanker.rerankHitsImpl(setup, query, result);
        expect.verifyScores(result);
        verifyHasMF(result, "public_value");
        verifyDoesNotHaveMF(result, "private_value");
    }
    @Test void matchFeaturesCanBeRemoved() {
        var setup = makeFullSetup(makeSumSpec(Collections.emptyList(), List.of("private_value")), 2,
                List.of("private_value"), Collections.emptyList());
        var query = makeQuery(Collections.emptyList());
        var factory = new HitFactory(List.of("private_value"));
        var result = makeResult(query, List.of(factory.create("a", 1, List.of(value("private_value", 3))),
                factory.create("b", 2, List.of(value("private_value", 7)))));
        var expect = Expect.make(List.of(hit("a", 3), hit("b", 7)));
        GlobalPhaseRanker.rerankHitsImpl(setup, query, result);
        expect.verifyScores(result);
        verifyDoesNotHaveMatchFeaturesField(result);
    }
    @Test void queryFeaturesCanBeUsed() {
        var setup = makeSimpleSetup(makeSumSpec(List.of("foo"), List.of("bar")), 2);
        var query = makeQuery(List.of(value("query(foo)", 7)));
        var factory = new HitFactory(List.of("bar"));
        var result = makeResult(query, List.of(factory.create("a", 1, List.of(value("bar", 2))),
                factory.create("b", 2, List.of(value("bar", 5)))));
        var expect = Expect.make(List.of(hit("a", 9), hit("b", 12)));
        GlobalPhaseRanker.rerankHitsImpl(setup, query, result);
        expect.verifyScores(result);
        verifyHasMF(result, "bar");
    }
    @Test void queryFeaturesCanBeUsedWhenPrepared() {
        var setup = makeSimpleSetup(makeSumSpec(List.of("foo"), List.of("bar")), 2);
        var query = makeQueryWithPrepare(List.of(value("query(foo)", 7)));
        var factory = new HitFactory(List.of("bar"));
        var result = makeResult(query, List.of(factory.create("a", 1, List.of(value("bar", 2))),
                factory.create("b", 2, List.of(value("bar", 5)))));
        var expect = Expect.make(List.of(hit("a", 9), hit("b", 12)));
        GlobalPhaseRanker.rerankHitsImpl(setup, query, result);
        expect.verifyScores(result);
        verifyHasMF(result, "bar");
    }
    @Test void withNormalizer() {
        var setup = makeNormSetup(makeSumSpec(Collections.emptyList(), List.of("bar")),
                List.of(makeNormalizer("foo", List.of(115.0, 65.0, 55.0, 45.0, 15.0), makeSumSpec(List.of("x"), List.of("bar")))));
        var query = makeQuery(List.of(value("query(x)", 5)));
        var factory = new HitFactory(List.of("bar"));
        var result = makeResult(query, List.of(factory.create("a", 1, List.of(value("bar", 10))),
                factory.create("b", 2, List.of(value("bar", 40))),
                factory.create("c", 3, List.of(value("bar", 50))),
                factory.create("d", 4, List.of(value("bar", 60))),
                factory.create("e", 5, List.of(value("bar", 110)))));
        var expect = Expect.make(List.of(hit("a", 15), hit("b", 44), hit("c", 53), hit("d", 62), hit("e", 111)));
        GlobalPhaseRanker.rerankHitsImpl(setup, query, result);
        expect.verifyScores(result);
    }
}
