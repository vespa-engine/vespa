// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tony Vaagenes
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ChainBuilderTest {

    private void addAtoG(ChainBuilder chainBuilder) throws ReflectiveOperationException {
        List<Class<? extends ChainedComponent>> componentTypes = new ArrayList<>();

        componentTypes.add(A.class);
        componentTypes.add(B.class);
        componentTypes.add(C.class);
        componentTypes.add(D.class);
        componentTypes.add(E.class);
        componentTypes.add(F.class);
        componentTypes.add(G.class);

        permute(componentTypes);

        for (Class<? extends ChainedComponent> searcherClass : componentTypes) {
            chainBuilder.addComponent(searcherClass.getDeclaredConstructor().newInstance());
        }
    }


    private void permute(List<Class<? extends ChainedComponent>> searcherTypes) {
        for (int i=0; i<searcherTypes.size(); ++i) {
            int j = (int) (Math.random() * searcherTypes.size());
            Class<? extends ChainedComponent> tmp = searcherTypes.get(i);
            searcherTypes.set(i,searcherTypes.get(j));
            searcherTypes.set(j, tmp);
        }
    }

    @Test
    public void testRegular() throws Exception {
        ChainBuilder chainBuilder = createDependencyHandler();

        addAtoG(chainBuilder);

        Chain<ChainedComponent> res = chainBuilder.orderNodes();

        Iterator<ChainedComponent> i = res.components().iterator();
        for (char j=0; j< 'G' - 'A'; ++j) {
            assertEquals(String.valueOf((char)('A' + j)), name(i.next()));
        }
    }

    @Test
    public void testCycle() throws Exception {

        ChainBuilder chainBuilder = createDependencyHandler();

        addAtoG(chainBuilder);
        chainBuilder.addComponent(new H());

        boolean cycle = false;
        try {
            chainBuilder.orderNodes();
        } catch (CycleDependenciesException e) {
            cycle = true;
        }
        assertTrue(cycle);
    }


    @Test
    public void testPhaseAndSearcher() {
        ChainBuilder depHandler = newChainBuilder();
        depHandler.addPhase(new Phase("phase1", set("phase2"), Collections.<String>emptySet()));
        depHandler.addPhase(new Phase("phase2", set("phase3"), set("phase1")));
        depHandler.addPhase(new Phase("phase3", Collections.<String>emptySet(), set("phase2", "phase1")));
        ChainedComponent first = new First();
        ChainedComponent second = new Second();

        depHandler.addComponent(first);
        depHandler.addComponent(second);
        assertEquals(depHandler.orderNodes().components(), Arrays.asList(first, second));

    }

    @Test
    public void testInputOrderPreservedWhenProvidesOverlap() {
        ChainBuilder chainBuilder = newChainBuilder();

        A a1 = new A();
        C c = new C();
        A a2 = new A();

        chainBuilder.addComponent(a1);
        chainBuilder.addComponent(c);
        chainBuilder.addComponent(a2);

        assertEquals(Arrays.asList(a1, c, a2), chainBuilder.orderNodes().components());
    }

    private ChainBuilder newChainBuilder() {
        return new ChainBuilder(new ComponentId("test"));
    }

    private Set<String> set(String... strings) {
        return new HashSet<>(Arrays.asList(strings));
    }

    @Before("phase1")
    static class First extends NoopComponent {

    }

    @After("phase3")
    static class Second extends NoopComponent {

    }

    @Test
    public void testAfterAll1() throws Exception {
        ChainBuilder chainBuilder = createDependencyHandler();
        ChainedComponent afterAll1 = new AfterAll();
        chainBuilder.addComponent(afterAll1);
        addAtoG(chainBuilder);

        List<ChainedComponent> resolution= chainBuilder.orderNodes().components();
        assertEquals(afterAll1,resolution.get(resolution.size()-1));
    }

    @Test
    public void testAfterAll2() throws Exception {
        ChainBuilder chainBuilder = createDependencyHandler();
        addAtoG(chainBuilder);
        ChainedComponent afterAll1 = new AfterAll();
        chainBuilder.addComponent(afterAll1);

        List<ChainedComponent> resolution = chainBuilder.orderNodes().components();
        assertEquals(afterAll1,resolution.get(resolution.size()-1));
    }

    @Test
    public void testAfterImplicitProvides()
            throws InstantiationException, IllegalAccessException {
        ChainBuilder chainBuilder = createDependencyHandler();
        ChainedComponent afterProvidesNothing=new AfterProvidesNothing();
        ChainedComponent providesNothing=new ProvidesNothing();
        chainBuilder.addComponent(afterProvidesNothing);
        chainBuilder.addComponent(providesNothing);
        List<ChainedComponent> resolution = chainBuilder.orderNodes().components();
        assertEquals(providesNothing,resolution.get(0));
        assertEquals(afterProvidesNothing,resolution.get(1));
    }

    private ChainBuilder createDependencyHandler() {
        ChainBuilder chainBuilder = newChainBuilder();
        chainBuilder.addPhase(new Phase("phase1", Collections.<String>emptySet(), Collections.<String>emptySet()));
        chainBuilder.addPhase(new Phase("phase2", Collections.<String>emptySet(), Collections.<String>emptySet()));
        chainBuilder.addPhase(new Phase("phase3", Collections.<String>emptySet(), Collections.<String>emptySet()));
        return chainBuilder;
    }

    private String name(ChainedComponent searcher) {
        return searcher.getClass().getSimpleName();
    }

    @Provides("A")
    static class A extends NoopComponent {
    }

    @Provides("B")
    @After("A")
    @Before({"D", "phase1"})
    static class B extends NoopComponent {
    }

    @Provides("C")
    @After("phase1")
    static class C extends NoopComponent {
    }

    @Provides("D")
    @After({"C","A"})
    static class D extends NoopComponent {
    }

    @Provides("E")
    @After({"B","D"})
    @Before("phase2")
    static class E extends NoopComponent {
    }

    @Provides("F")
    @After("phase2")
    static class F extends NoopComponent {
    }

    @Provides("G")
    @After("F")
    static class G extends NoopComponent {
    }

    @Provides("H")
    @Before("A")
    @After("F")
    static class H extends NoopComponent {
    }

    @Provides("AfterAll")
    @After("*")
    static class AfterAll extends NoopComponent {
    }

    static class ProvidesNothing extends NoopComponent {
    }

    @After("ProvidesNothing")
    static class AfterProvidesNothing extends NoopComponent {
    }

    static class NoopComponent extends ChainedComponent {
    }

}
