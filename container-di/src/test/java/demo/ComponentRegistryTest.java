// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package demo;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.provider.ComponentRegistry;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;


/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class ComponentRegistryTest extends Base {
    public static class SearchHandler extends AbstractComponent {
        private final ComponentRegistry<Searcher> searchers;

        public SearchHandler(ComponentRegistry<Searcher> searchers) {
            this.searchers = searchers;
        }
    }

    public static class Searcher extends AbstractComponent {}

    public static class FooSearcher extends Searcher {}
    public static class BarSearcher extends Searcher {}

    @Test
    public void require_that_component_registry_can_be_injected() {
        register(SearchHandler.class);
        register(FooSearcher.class);
        register(BarSearcher.class);
        complete();

        SearchHandler handler = getInstance(SearchHandler.class);

        ComponentRegistry<Searcher> searchers = handler.searchers;
        assertNotNull(searchers.getComponent(toId(FooSearcher.class)));
        assertNotNull(searchers.getComponent(toId(BarSearcher.class)));
    }
}
