// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.net.URI;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.selection.FederationTarget;
import com.yahoo.search.federation.selection.TargetSelector;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.Execution.Context;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Tony Vaagenes
 */
public class FederationSearcherTest {

    private static final String hasBeenFilled = "hasBeenFilled";

    @Test
    void require_that_hits_are_not_automatically_filled() {
        Result result = federationToSingleAddHitSearcher().search();
        assertNotFilled(firstHitInFirstGroup(result));
    }

    @Test
    void require_that_hits_can_be_filled() {
        Result result = federationToSingleAddHitSearcher().searchAndFill();
        assertFilled(firstHitInFirstGroup(result));
    }

    @Test
    void require_that_hits_can_be_filled_when_moved() {
        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitSearcher());
        tester.addSearchChain("chain2", new AddHitSearcher());

        Result result = tester.search();

        Result reorganizedResult = new Result(result.getQuery());
        HitGroup hit1 = new HitGroup();
        HitGroup nestedHitGroup = new HitGroup();

        hit1.add(nestedHitGroup);
        reorganizedResult.hits().add(hit1);

        HitGroup chain1Group = (HitGroup) result.hits().get(0);
        HitGroup chain2Group = (HitGroup) result.hits().get(1);

        nestedHitGroup.add(chain1Group.get(0));
        reorganizedResult.hits().add(chain2Group.get(0));
        reorganizedResult.hits().add(nestedHitGroup);

        tester.fill(reorganizedResult);
        assertFilled(nestedHitGroup.get(0));
        assertFilled(chain2Group.get(0));

    }

    @Test
    void require_that_hits_can_be_filled_for_multiple_chains_and_queries() {
        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitSearcher());
        tester.addSearchChain("chain2", new ModifyQueryAndAddHitSearcher("modified1"));
        tester.addSearchChain("chain3", new ModifyQueryAndAddHitSearcher("modified2"));

        Result result = tester.search();
        tester.fill(result);
        assertEquals(3, result.hits().getConcreteSize());
        for (Iterator<Hit> i = result.hits().deepIterator(); i.hasNext(); )
            assertFilled(i.next());
    }

    @Test
    void require_that_hits_that_time_out_in_fill_are_removed() {
        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitSearcher());
        tester.addSearchChain("chain2", new TimeoutInFillSearcher());

        Query query = new Query();
        query.setTimeout(20000);
        Result result = tester.search(query);
        tester.fill(result);
        assertEquals(1, result.hits().getConcreteSize());
        for (Iterator<Hit> i = result.hits().deepIterator(); i.hasNext(); )
            assertFilled(i.next());
        assertEquals("Timed out", result.hits().getError().getMessage());
    }

    @Test
    void require_that_optional_search_chains_does_not_delay_federation() {
        BlockingSearcher blockingSearcher = new BlockingSearcher();

        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitSearcher());
        tester.addOptionalSearchChain("chain2", blockingSearcher);

        Result result = tester.searchAndFill();
        assertEquals(2, result.getHitCount());
        assertTrue(result.hits().get(0) instanceof HitGroup);
        assertTrue(result.hits().get(1) instanceof HitGroup);
        HitGroup chain1Result = (HitGroup) result.hits().get(0);
        HitGroup chain2Result = (HitGroup) result.hits().get(1);

        // Verify chain1 result: One filled hit
        assertEquals(1, chain1Result.size());
        assertFilled(getFirstHit(chain1Result));

        // Verify chain2 result: A timeout error
        assertEquals(1, chain2Result.size());
        assertNotNull(chain2Result.getErrorHit());
        ErrorHit errorHit = chain2Result.getErrorHit();
        assertEquals(1, errorHit.errors().size());
        ErrorMessage error = errorHit.errors().iterator().next();
        assertEquals("chain2", error.getSource());
        assertEquals(ErrorMessage.timeoutCode, error.getCode());
        assertEquals("Timed out", error.getMessage());
        assertEquals("Error in execution of chain 'chain2': Chain timed out.", error.getDetailedMessage());
    }

    @Test
    void require_that_calling_a_single_slow_source_with_long_timeout_does_not_delay_federation() {
        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1",
                new FederationOptions().setUseByDefault(true).setRequestTimeoutInMilliseconds(3600 * 1000),
                new BlockingSearcher());

        Query query = new Query();
        query.setTimeout(50); // make the test run faster
        Result result = tester.search(query);
        assertEquals(1, result.hits().size());
        assertTrue(result.hits().get(0) instanceof HitGroup);
        HitGroup chain1Result = (HitGroup) result.hits().get(0);
        assertEquals(1, chain1Result.size());
        assertTrue(chain1Result.asList().get(0) instanceof ErrorHit);
        ErrorHit errorHit = (ErrorHit) chain1Result.get(0);
        assertEquals(1, errorHit.errors().size());
        ErrorMessage error = errorHit.errors().iterator().next();
        assertEquals("chain1", error.getSource());
        assertEquals(ErrorMessage.timeoutCode, error.getCode());
        assertEquals("Timed out", error.getMessage());
    }

    @Test
    void custom_federation_target() {
        ComponentId targetSelectorId = ComponentId.fromString("TargetSelector");
        ComponentRegistry<TargetSelector> targetSelectors = new ComponentRegistry<>();
        targetSelectors.register(targetSelectorId, new TestTargetSelector());

        FederationSearcher searcher = new FederationSearcher(
                new FederationConfig(new FederationConfig.Builder().targetSelector(targetSelectorId.toString())),
                targetSelectors);

        Query query = new Query();
        query.setTimeout(20000);
        Result result = new Execution(searcher, Context.createContextStub()).search(query);
        HitGroup myChainGroup = (HitGroup) result.hits().get(0);
        assertEquals(myChainGroup.getId(), new URI("source:myChain"));
        assertEquals(myChainGroup.get(0).getId(), new URI("myHit"));
    }

    @Test
    void target_selectors_can_have_multiple_targets() {
        ComponentId targetSelectorId = ComponentId.fromString("TestMultipleTargetSelector");
        ComponentRegistry<TargetSelector> targetSelectors = new ComponentRegistry<>();
        targetSelectors.register(targetSelectorId, new TestMultipleTargetSelector());

        FederationSearcher searcher = new FederationSearcher(
                new FederationConfig(new FederationConfig.Builder().targetSelector(targetSelectorId.toString())),
                targetSelectors);

        Query query = new Query();
        query.setTimeout(20000);
        Result result = new Execution(searcher, Context.createContextStub()).search(query);

        Iterator<Hit> hitsIterator = result.hits().deepIterator();
        Hit hit1 = hitsIterator.next();
        Hit hit2 = hitsIterator.next();

        assertEquals(hit1.getSource(), "chain1");
        assertEquals(hit2.getSource(), "chain2");

        assertEquals(hit1.getField("data"), "modifyTargetQuery:custom-data:1");
        assertEquals(hit2.getField("data"), "modifyTargetQuery:custom-data:2");
    }

    private Hit getFirstHit(Hit hitGroup) {
        if (hitGroup instanceof HitGroup)
            return ((HitGroup) hitGroup).get(0);
        else
            throw new IllegalArgumentException("Expected HitGroup");
    }

    private static void assertFilled(Hit hit) {
        if (hit.isMeta()) return;
        assertTrue((Boolean)hit.getField(hasBeenFilled));
    }

    private static void assertNotFilled(Hit hit) {
        assertNull(hit.getField(hasBeenFilled));
    }

    private FederationTester federationToSingleAddHitSearcher() {
        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitSearcher());
        return tester;
    }

    private static Hit firstHit(Result result) {
        return result.hits().get(0);
    }

    private static Hit firstHitInFirstGroup(Result result) {
        return ((HitGroup)firstHit(result)).get(0);
    }

    private static class AddHitSearcher extends Searcher {

        protected Hit hit = createHit();

        private Hit createHit() {
            Hit hit = new Hit("dummy");
            hit.setFillable();
            return hit;
        }

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            result.hits().add(hit);
            return result;
        }

        @Override
        public void fill(Result result, String summaryClass, Execution execution) {
            if (firstHit(result) != hit) {
                throw new RuntimeException("Unknown hit");
            }
            firstHit(result).setField(hasBeenFilled, true);
        }

    }

    private static class TimeoutInFillSearcher extends Searcher {

        private Hit createHit(String id) {
            Hit hit = new Hit(id);
            hit.setFillable();
            return hit;
        }

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            result.hits().add(createHit("timeout1"));
            result.hits().add(createHit("timeout2"));
            return result;
        }

        @Override
        public void fill(Result result, String summaryClass, Execution execution) {
            throw new TimeoutException("TimeoutInFillSearcher always time out in fill");
        }

    }

    private static class ModifyQueryAndAddHitSearcher extends AddHitSearcher {

        private final String marker;

        ModifyQueryAndAddHitSearcher(String marker) {
            super();
            this.marker = marker;
        }

        @Override
        public Result search(Query query, Execution execution) {
            query.getModel().getQueryTree().setRoot(new WordItem(marker));
            Result result = execution.search(query);
            result.hits().add(hit);
            return result;
        }

    }

    private static class TestTargetSelector implements TargetSelector<String> {
        String keyName = getClass().getName();

        @Override
        public Collection<FederationTarget<String>> getTargets(Query query, ChainRegistry<Searcher> searcherChainRegistry) {
            return List.of(
                    new FederationTarget<>(new Chain<>("myChain", List.of()), new FederationOptions(), "hello"));
        }

        @Override
        public void modifyTargetQuery(FederationTarget<String> target, Query query) {
            checkTarget(target);
            query.properties().set(keyName, "called");
        }

        @Override
        public void modifyTargetResult(FederationTarget<String> target, Result result) {
            checkTarget(target);
            assertEquals(result.getQuery().properties().getString(keyName), "called");
            result.hits().add(new Hit("myHit"));
        }

        private void checkTarget(FederationTarget<String> target) {
            assertEquals(target.getCustomData(), "hello");
            assertEquals(target.getChain().getId(), ComponentId.fromString("myChain"));
        }
    }

    private static class TestMultipleTargetSelector implements TargetSelector<String> {

        String keyName = getClass().getName();

        @Override
        public Collection<FederationTarget<String>> getTargets(Query query, ChainRegistry<Searcher> searcherChainRegistry) {
            return Arrays.asList(createTarget(1), createTarget(2));
        }

        private FederationTarget<String> createTarget(int number) {
            return new FederationTarget<>(new Chain<>("chain" + number, List.of()),
                                          new FederationOptions(),
                                          "custom-data:" + number);
        }

        @Override
        public void modifyTargetQuery(FederationTarget<String> target, Query query) {
            query.properties().set(keyName, "modifyTargetQuery:" + target.getCustomData());
        }

        @Override
        public void modifyTargetResult(FederationTarget<String> target, Result result) {
            Hit hit = new Hit("MyHit" + target.getCustomData());
            hit.setField("data", result.getQuery().properties().get(keyName));
            result.hits().add(hit);
        }
    }

}
