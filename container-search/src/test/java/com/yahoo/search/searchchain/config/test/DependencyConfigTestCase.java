// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.config.test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.yahoo.component.chain.dependencies.After;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChainRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author Tony Vaagenes
 */
public class DependencyConfigTestCase {

    private static HandlersConfigurerTestWrapper configurer;

    private static SearchChainRegistry registry;

    public static final String root = "src/test/java/com/yahoo/search/searchchain/config/test/dependencyConfig";

    @BeforeAll
    public static void createComponentsConfig() throws IOException {
        SearchChainConfigurerTestCase.
                createComponentsConfig(root + "/chains.cfg", root + "/handlers.cfg", root + "/components.cfg");
        setUp();
    }

    @AfterAll
    public static void removeComponentsConfig() {
        new File(root + "/components.cfg").delete();
        tearDown();
    }

    public static void setUp() {
        String configId = "dir:" + root;
        configurer = new HandlersConfigurerTestWrapper(configId);
        registry=((SearchHandler) configurer.getRequestHandlerRegistry().getComponent("com.yahoo.search.handler.SearchHandler")).getSearchChainRegistry();
    }

    public static void tearDown() {
        configurer.shutdown();
    }

    @Provides("P")
    @Before("B")
    @After("A")
    public static class Searcher1 extends Searcher {

        public Result search(Query query,Execution execution) {
            return execution.search(query);
        }

    }

    @Test
    void test() {
        Dependencies dependencies = registry.getSearcherRegistry().getComponent(Searcher1.class.getName()).getDependencies();

        assertTrue(dependencies.provides().containsAll(Arrays.asList("P", "P1", "P2", Searcher1.class.getSimpleName())));
        assertTrue(dependencies.before().containsAll(Arrays.asList("B", "B1", "B2")));
        assertTrue(dependencies.after().containsAll(Arrays.asList("A", "A1", "A2")));
    }
}
