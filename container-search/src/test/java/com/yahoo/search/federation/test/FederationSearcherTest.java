// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.net.URI;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.FederationConfig;
import com.yahoo.search.federation.FederationSearcher;
import com.yahoo.search.federation.TimeoutException;
import com.yahoo.search.federation.selection.FederationTarget;
import com.yahoo.search.federation.selection.TargetSelector;
import com.yahoo.search.federation.StrictContractsConfig;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.Execution.Context;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author tonytv
 */
public class FederationSearcherTest {

    private static final String hasBeenFilled = "hasBeenFilled";

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

    @Test
    public void require_that_hits_are_not_automatically_filled() {
        Result result = federationToSingleAddHitSearcher().search();
        assertNotFilled(firstHitInFirstGroup(result));
    }

    @Test
    public void require_that_hits_can_be_filled() {
        Result result = federationToSingleAddHitSearcher().searchAndFill();
        assertFilled(firstHitInFirstGroup(result));
    }

    @Test
    public void require_that_hits_can_be_filled_when_moved() {
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
    public void require_that_hits_can_be_filled_for_multiple_chains_and_queries() {
        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitSearcher());
        tester.addSearchChain("chain2", new ModifyQueryAndAddHitSearcher("modified1"));
        tester.addSearchChain("chain3", new ModifyQueryAndAddHitSearcher("modified2"));

        Result result = tester.search();
        tester.fill(result);
        assertEquals(3, result.hits().getConcreteSize());
        for (Iterator<Hit> i = result.hits().deepIterator(); i.hasNext();)
            assertFilled(i.next());
    }

    @Test
    public void require_that_hits_that_time_out_in_fill_are_removed() {
        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitSearcher());
        tester.addSearchChain("chain2", new TimeoutInFillSearcher());

        Query query = new Query();
        Result result = tester.search(query);
        tester.fill(result);
        assertEquals(1, result.hits().getConcreteSize());
        for (Iterator<Hit> i = result.hits().deepIterator(); i.hasNext();)
            assertFilled(i.next());
        assertEquals("Timed out", result.hits().getError().getMessage());
    }

    @Test
    public void require_that_optional_search_chains_does_not_delay_federation() {
        BlockingSearcher blockingSearcher = new BlockingSearcher();

        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitSearcher());
        tester.addOptionalSearchChain("chain2", blockingSearcher);

        Result result = tester.searchAndFill();
        assertThat(getNonErrorHits(result).size(), is(1));
        assertFilled(getFirstHit(getNonErrorHits(result).get(0)));
        assertNotNull(result.hits().getError());
    }

    @Test
    public void require_that_calling_a_single_slow_source_with_long_timeout_does_not_delay_federation() {
        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1",
                              new FederationOptions().setUseByDefault(true).setRequestTimeoutInMilliseconds(3600 * 1000),
                              new BlockingSearcher() );

        Query query = new Query();
        query.setTimeout(50); // make the test run faster
        Result result = tester.search(query);
        assertThat(getNonErrorHits(result).size(), is(0));
        assertNotNull(result.hits().getError());
    }

    private Hit getFirstHit(Hit hitGroup) {
        if (hitGroup instanceof HitGroup)
            return ((HitGroup) hitGroup).get(0);
        else
            throw new IllegalArgumentException("Expected HitGroup");
    }

    private List<Hit> getNonErrorHits(Result result) {
        List<Hit> nonErrorHits = new ArrayList<>();
        for (Hit hit : result.hits()) {
            if (!(hit instanceof ErrorHit))
                nonErrorHits.add(hit);
        }

        return nonErrorHits;
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

    @Test
    public void custom_federation_target() {
        ComponentId targetSelectorId = ComponentId.fromString("TargetSelector");
        ComponentRegistry<TargetSelector> targetSelectors = new ComponentRegistry<>();
        targetSelectors.register(targetSelectorId, new TestTargetSelector());

        FederationSearcher searcher = new FederationSearcher(
                new FederationConfig(new FederationConfig.Builder().targetSelector(targetSelectorId.toString())),
                new StrictContractsConfig(new StrictContractsConfig.Builder()),
                targetSelectors);

        Result result = new Execution(searcher, Context.createContextStub()).search(new Query());
        HitGroup myChainGroup = (HitGroup) result.hits().get(0);
        assertThat(myChainGroup.getId(), is(new URI("source:myChain")));
        assertThat(myChainGroup.get(0).getId(), is(new URI("myHit")));
    }

    static class TestTargetSelector implements TargetSelector<String> {
        String keyName = getClass().getName();

        @Override
        public Collection<FederationTarget<String>> getTargets(Query query, ChainRegistry<Searcher> searcherChainRegistry) {
            return Arrays.asList(
                    new FederationTarget<>(new Chain<>("myChain", Collections.<Searcher>emptyList()), new FederationOptions(), "hello"));
        }

        @Override
        public void modifyTargetQuery(FederationTarget<String> target, Query query) {
            checkTarget(target);
            query.properties().set(keyName, "called");
        }

        @Override
        public void modifyTargetResult(FederationTarget<String> target, Result result) {
            checkTarget(target);
            assertThat(result.getQuery().properties().getString(keyName), is("called"));
            result.hits().add(new Hit("myHit"));
        }

        private void checkTarget(FederationTarget<String> target) {
            assertThat(target.getCustomData(), is("hello"));
            assertThat(target.getChain().getId(), is(ComponentId.fromString("myChain")));
        }
    }

    static class TestMultipleTargetSelector implements TargetSelector<String> {
        String keyName = getClass().getName();

        @Override
        public Collection<FederationTarget<String>> getTargets(Query query, ChainRegistry<Searcher> searcherChainRegistry) {
            return Arrays.asList(createTarget(1), createTarget(2));
        }

        private FederationTarget<String> createTarget(int number) {
            return new FederationTarget<>(new Chain<>("chain" + number, Collections.<Searcher>emptyList()),
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

    @Test
    public void target_selectors_can_have_multiple_targets() {
        ComponentId targetSelectorId = ComponentId.fromString("TestMultipleTargetSelector");
        ComponentRegistry<TargetSelector> targetSelectors = new ComponentRegistry<>();
        targetSelectors.register(targetSelectorId, new TestMultipleTargetSelector());

        FederationSearcher searcher = new FederationSearcher(
                new FederationConfig(new FederationConfig.Builder().targetSelector(targetSelectorId.toString())),
                new StrictContractsConfig(new StrictContractsConfig.Builder()),
                targetSelectors);

        Result result = new Execution(searcher, Context.createContextStub()).search(new Query());

        Iterator<Hit> hitsIterator = result.hits().deepIterator();
        Hit hit1 = hitsIterator.next();
        Hit hit2 = hitsIterator.next();

        assertThat(hit1.getSource(), is("chain1"));
        assertThat(hit2.getSource(), is("chain2"));

        assertThat((String)hit1.getField("data"), is("modifyTargetQuery:custom-data:1"));
        assertThat((String)hit2.getField("data"), is("modifyTargetQuery:custom-data:2"));
    }

}
