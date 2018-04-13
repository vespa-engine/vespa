// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.intent.model.*;
import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.PageTemplateRegistry;
import com.yahoo.search.pagetemplates.PageTemplateSearcher;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.PageElement;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.text.interpretation.Interpretation;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class PageTemplateSearcherTestCase {

    @Test
    public void testSearcher() {
        PageTemplateSearcher s = new PageTemplateSearcher(createPageTemplateRegistry(), new FirstChoiceResolver());
        Chain<Searcher> chain = new Chain<>(s,new MockFederator());

        {
            // No template specified, should use default
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(new Query("?query=foo&page.resolver=native.deterministic"));
            assertSources("source1 source2",result);
        }

        {
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(new Query("?query=foo&page.id=oneSource&page.resolver=native.deterministic"));
            assertSources("source1",result);
        }

        {
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(new Query("?query=foo&page.id=twoSources&model.sources=source1&page.resolver=native.deterministic"));
            assertSources("source1",result);
        }

        {
            Query query=new Query("?query=foo&page.resolver=native.deterministic");
            addIntentModelSpecifyingSource3(query);
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertSources("source1 source2",result);
        }

        {
            Query query=new Query("?query=foo&page.id=twoSourcesAndAny&page.resolver=native.deterministic");
            addIntentModelSpecifyingSource3(query);
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertSources("source1 source2 source3",result);
        }

        {
            Query query=new Query("?query=foo&page.id=anySource&page.resolver=native.deterministic");
            addIntentModelSpecifyingSource3(query);
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertSources("source3",result);
        }

        {
            Query query=new Query("?query=foo&page.id=anySource&page.resolver=native.deterministic");
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertTrue(query.getModel().getSources().isEmpty());
            assertNotNull(result.hits().get("source1"));
            assertNotNull(result.hits().get("source2"));
            assertNotNull(result.hits().get("source3"));
        }

        {
            Query query=new Query("?query=foo&page.id=choiceOfSources&page.resolver=native.deterministic");
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertSources("source1 source2","source2",result);
        }

        {
            Query query=new Query("?query=foo&page.id=choiceOfSources&page.resolver=test.firstChoice");
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertSources("source1 source2","source1",result);
        }

        { // Specifying two templates, should pick the last
            Query query=new Query("?query=foo&page.id=threeSources+oneSource&page.resolver=native.deterministic");
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertSources("source1 source2 source3","source1",result);
        }

        { // Specifying two templates as a list, should override the page.id setting
            Query query=new Query("?query=foo&page.id=anySource&page.resolver=native.deterministic");
            query.properties().set("page.idList",Arrays.asList("oneSource","threeSources"));
            Result result=new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertSources("source1 source2 source3","source1 source2 source3",result);
        }

        {
            try {
                Query query=new Query("?query=foo&page.id=oneSource+choiceOfSources&page.resolver=noneSuch");
                new Execution(chain, Execution.Context.createContextStub()).search(query);
                fail("Expected exception");
            }
            catch (IllegalArgumentException e) {
                assertEquals("No page template resolver 'noneSuch'",e.getMessage());
            }
        }

    }

    private PageTemplateRegistry createPageTemplateRegistry() {
        PageTemplateRegistry registry=new PageTemplateRegistry();

        PageTemplate twoSources=new PageTemplate(new ComponentId("default"));
        twoSources.getSection().elements().add(new com.yahoo.search.pagetemplates.model.Source("source1"));
        twoSources.getSection().elements().add(new com.yahoo.search.pagetemplates.model.Source("source2"));
        registry.register(twoSources);

        PageTemplate oneSource=new PageTemplate(new ComponentId("oneSource"));
        oneSource.getSection().elements().add(new com.yahoo.search.pagetemplates.model.Source("source1"));
        registry.register(oneSource);

        PageTemplate threeSources=new PageTemplate(new ComponentId("threeSources"));
        threeSources.getSection().elements().add(new com.yahoo.search.pagetemplates.model.Source("source1"));
        threeSources.getSection().elements().add(new com.yahoo.search.pagetemplates.model.Source("source2"));
        threeSources.getSection().elements().add(new com.yahoo.search.pagetemplates.model.Source("source3"));
        registry.register(threeSources);

        PageTemplate twoSourcesAndAny=new PageTemplate(new ComponentId("twoSourcesAndAny"));
        twoSourcesAndAny.getSection().elements().add(new com.yahoo.search.pagetemplates.model.Source("source1"));
        twoSourcesAndAny.getSection().elements().add(new com.yahoo.search.pagetemplates.model.Source("source2"));
        twoSourcesAndAny.getSection().elements().add(com.yahoo.search.pagetemplates.model.Source.any);
        registry.register(twoSourcesAndAny);

        PageTemplate anySource=new PageTemplate(new ComponentId("anySource"));
        anySource.getSection().elements().add(com.yahoo.search.pagetemplates.model.Source.any);
        registry.register(anySource);

        PageTemplate choiceOfSources=new PageTemplate(new ComponentId("choiceOfSources"));
        List<PageElement> alternatives=new ArrayList<>();
        alternatives.add(new com.yahoo.search.pagetemplates.model.Source("source1"));
        alternatives.add(new com.yahoo.search.pagetemplates.model.Source("source2"));
        choiceOfSources.getSection().elements().add(Choice.createSingletons(alternatives));
        registry.register(choiceOfSources);

        registry.freeze();
        return registry;
    }

    private void addIntentModelSpecifyingSource3(Query query) {
        IntentModel intentModel=new IntentModel();
        InterpretationNode interpretation=new InterpretationNode(new Interpretation("ignored"));
        IntentNode intent=new IntentNode(new Intent("ignored"),1.0);
        intent.children().add(new SourceNode(new com.yahoo.search.intent.model.Source("source3"),1.0));
        interpretation.children().add(intent);
        intentModel.children().add(interpretation);
        intentModel.setTo(query);
    }

    private void assertSources(String expectedSourceString,Result result) {
        assertSources(expectedSourceString,expectedSourceString,result);
    }

    private void assertSources(String expectedQuerySourceString,String expectedResultSourceString,Result result) {
        Set<String> expectedQuerySources=new HashSet<>(Arrays.asList(expectedQuerySourceString.split(" ")));
        assertEquals(expectedQuerySources,result.getQuery().getModel().getSources());

        Set<String> expectedResultSources=new HashSet<>(Arrays.asList(expectedResultSourceString.split(" ")));
        for (String sourceName : Arrays.asList("source1 source2 source3".split(" "))) {
            if (expectedResultSources.contains(sourceName))
                assertNotNull("Result contains '" + sourceName + "'",result.hits().get(sourceName));
            else
                assertNull("Result does not contain '" + sourceName + "'",result.hits().get(sourceName));
        }
    }

    private static class MockFederator extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Result result=new Result(query);
            for (String sourceName : Arrays.asList("source1 source2 source3".split(" ")))
                if (query.getModel().getSources().isEmpty() || query.getModel().getSources().contains(sourceName))
                    result.hits().add(createSource(sourceName));
            return result;
        }

        private HitGroup createSource(String sourceName) {
            HitGroup hitGroup=new HitGroup("source:" + sourceName);
            Hit hit=new Hit(sourceName);
            hit.setSource(sourceName);
            hitGroup.add(hit);
            return hitGroup;
        }

    }

    /** Like the deterministic resolver except that it takes the <i>first</i> option of all choices */
    private static class FirstChoiceResolver extends DeterministicResolver {

        public FirstChoiceResolver() {
            super("test.firstChoice");
        }

        /** Chooses the first alternative of any choice */
        @Override
        public void resolve(Choice choice, Query query, Result result, Resolution resolution) {
            resolution.addChoiceResolution(choice,0);
        }

    }

}
