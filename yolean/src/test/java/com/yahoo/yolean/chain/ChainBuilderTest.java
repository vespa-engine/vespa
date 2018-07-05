// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.yolean.chain.Dependencies.after;
import static com.yahoo.yolean.chain.Dependencies.before;
import static com.yahoo.yolean.chain.Dependencies.provides;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertTrue;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class ChainBuilderTest {

    static class Filter {

    }

    static class FilterA extends Filter {

    }

    static class FilterB extends Filter {

    }

    static class FilterExtendsA extends FilterA {

    }

    static class FilterExtendsB extends FilterB {

    }

    @Provides("A")
    static class ProvidesA extends Filter {

    }

    @Provides("B")
    static class ProvidesB extends Filter {

    }

    @Before("A")
    static class BeforeA extends Filter {

    }

    @After("A")
    static class AfterA extends Filter {

    }

    @Before("*")
    @Provides("BeforeAll")
    static class BeforeAll extends Filter {

    }

    @After("*")
    @Provides("AfterAll")
    static class AfterAll extends Filter {

    }

    @Before({ "BeforeAll", "*" })
    static class BeforeBeforeAll extends Filter {

    }

    @After({ "AfterAll", "*" })
    static class AfterAfterAll extends Filter {

    }

    static class ExtendsProvidesA extends ProvidesA {

    }

    @Provides("ExtendsA")
    static class ProvidesA_and_ProvidesExtendsA extends ProvidesA {

    }

    @Before("B")
    static class BeforeA_and_BeforeB extends BeforeA {

    }

    @Test
    public void build_empty_chain() {
        Chain<Filter> chain = getChain().build();
        assertTrue(chain.isEmpty());
        assertThat(chain.id(), is("myChain"));
    }

    @Test
    public void filters_without_dependencies_are_not_reordered() {
        List<Filter> filters = new ArrayList<>();

        ChainBuilder<Filter> chain = new ChainBuilder<>("myChain");
        for (int i = 0; i < 10; ++i) {
            Filter filter = new Filter();
            filters.add(filter);
            chain.add(filter);
        }

        assertThat(chain.build(), contains(filters.toArray()));
    }

    @Test(expected = ChainCycleException.class)
    public void cycles_are_detected() {
        Filter a = new Filter();
        Filter b = new Filter();

        getChain().add(b, before(a)).add(a, before(b)).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void adding_same_instance_twice_is_illegal() {
        Filter a = new Filter();

        getChain().add(a).add(a).build();
    }

    @Test
    public void before_instance() {
        Filter a = new Filter();
        Filter b = new Filter();

        Chain<Filter> chain = getChain().
                add(b).add(a, before(b)).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void after_instance() {
        Filter a = new Filter();
        Filter b = new Filter();

        Chain<Filter> chain = getChain().
                add(b, after(a)).add(a).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void before_class() {
        Filter a = new FilterA();
        Filter b = new FilterB();

        Chain<Filter> chain = getChain().
                add(b).add(a, before(b.getClass())).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void after_class() {
        Filter a = new FilterA();
        Filter b = new FilterB();

        Chain<Filter> chain = getChain().
                add(b, after(a.getClass())).add(a).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void before_subclass() {
        Filter a = new FilterA();
        Filter b = new FilterExtendsB();

        Chain<Filter> chain = getChain().
                add(b).add(a, before(FilterB.class)).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void after_subclass() {
        Filter a = new FilterExtendsA();
        Filter b = new FilterB();

        Chain<Filter> chain = getChain().
                add(b, after(FilterA.class)).add(a).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void before_provided_name() {
        Filter a = new Filter();
        Filter b = new Filter();

        Chain<Filter> chain = getChain().
                add(b, provides("B")).add(a, before("B")).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void after_provided_name() {
        Filter a = new Filter();
        Filter b = new Filter();

        Chain<Filter> chain = getChain().
                add(b, after("A")).add(a, provides("A")).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void before_provided_name_in_annotations() {
        Filter providesA = new ProvidesA();
        Filter beforeA = new BeforeA();

        Chain<Filter> chain = getChain().
                add(providesA).add(beforeA).build();

        assertThat(chain, is(new Chain<>("myChain", beforeA, providesA)));
    }

    @Test
    public void after_provided_name_in_annotations() {
        Filter providesA = new ProvidesA();
        Filter afterA = new AfterA();

        Chain<Filter> chain = getChain().
                add(afterA).add(providesA).build();

        assertThat(chain, is(new Chain<>("myChain", providesA, afterA)));
    }

    @Test
    public void before_all() {
        Filter a = new Filter();
        Filter b = new Filter();

        Chain<Filter> chain = getChain().
                add(b).add(a, before("*")).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void after_all() {
        Filter a = new Filter();
        Filter b = new Filter();

        Chain<Filter> chain = getChain().
                add(b, after("*")).add(a).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void before_all_annotation() {
        Filter a = new Filter();
        Filter beforeAll = new BeforeAll();

        Chain<Filter> chain = getChain().
                add(a).add(beforeAll).build();

        assertThat(chain, is(new Chain<>("myChain", beforeAll, a)));
    }

    @Test
    public void after_all_annotation() {
        Filter a = new Filter();
        Filter afterAll = new AfterAll();

        Chain<Filter> chain = getChain().
                add(afterAll).add(a).build();

        assertThat(chain, is(new Chain<>("myChain", a, afterAll)));
    }

    @Test
    public void before_all_annotated_component_can_be_before_another_component_that_is_also_before_all_annotated() {
        Filter beforeAll = new BeforeAll();
        Filter beforeBeforeAll = new BeforeBeforeAll();

        Chain<Filter> chain = getChain().
                add(beforeAll).add(beforeBeforeAll).build();

        assertThat(chain, is(new Chain<>("myChain", beforeBeforeAll, beforeAll)));
    }

    @Test
    public void after_all_annotated_component_can_be_after_another_component_that_is_also_after_all_annotated() {
        Filter afterAll = new AfterAll();
        Filter afterAfterAll = new AfterAfterAll();

        Chain<Filter> chain = getChain().
                add(afterAfterAll).add(afterAll).build();

        assertThat(chain, is(new Chain<>("myChain", afterAll, afterAfterAll)));
    }

    @Test
    public void component_that_is_not_annotated_can_be_before_a_before_all_annotated_component() {
        Filter first = new Filter();
        Filter beforeAll = new BeforeAll();

        Chain<Filter> chain = getChain().
                add(beforeAll).add(first, before("BeforeAll")).build();

        assertThat(chain, is(new Chain<>("myChain", first, beforeAll)));
    }

    @Test
    public void component_that_is_not_annotated_can_be_after_an_after_all_annotated_component() {
        Filter last = new Filter();
        Filter afterAll = new AfterAll();

        Chain<Filter> chain = getChain().
                add(last, after("AfterAll")).add(afterAll).build();

        assertThat(chain, is(new Chain<>("myChain", afterAll, last)));
    }

    @Test
    public void class_name_is_always_provided() {
        Filter a = new FilterA();
        Filter b = new FilterB();

        Chain<Filter> chain = getChain().
                add(b, after(a.getClass().getName())).add(a).build();

        assertThat(chain, is(new Chain<>("myChain", a, b)));
    }

    @Test
    public void provides_annotation_on_superclass_is_inherited_by_subclasses() {
        Filter extendsA = new ExtendsProvidesA();
        Filter first = new FilterA();
        Filter last = new FilterB();

        Chain<Filter> chain = getChain().
                add(last, after("A")).add(first, before("A")).add(extendsA).build();

        assertThat(chain, is(new Chain<>("myChain", first, extendsA, last)));
    }

    @Test
    public void provides_annotation_on_superclass_is_inherited_by_a_subclass_that_has_its_own_provides_annotation() {
        Filter extendsA = new ProvidesA_and_ProvidesExtendsA();
        Filter first = new FilterA();
        Filter last = new FilterB();

        Chain<Filter> chain = getChain().
                add(last, after("A")).add(first, before("ExtendsA")).add(extendsA).build();

        assertThat(chain, is(new Chain<>("myChain", first, extendsA, last)));
    }

    @Test
    public void before_annotation_on_superclass_is_inherited_by_a_subclass_that_has_its_own_before_annotation() {
        Filter beforeA_and_beforeB = new BeforeA_and_BeforeB();
        Filter A = new ProvidesA();
        Filter B = new ProvidesB();

        Chain<Filter> chain = getChain().
                add(A, before("*")).add(beforeA_and_beforeB).add(B).build();
        assertThat(chain, is(new Chain<>("myChain", beforeA_and_beforeB, A, B)));
    }

    @Test
    public void add_accepts_multiple_dependencies() {
        Filter a = new Filter();
        Filter b = new Filter();
        Filter c = new Filter();

        Chain<Filter> chain = getChain().
                add(a).add(c).add(b, after(a), before(c)).build();

        assertThat(chain, is(new Chain<>("myChain", a, b, c)));
    }

    private ChainBuilder<Filter> getChain() {
        return new ChainBuilder<>("myChain");
    }
}
