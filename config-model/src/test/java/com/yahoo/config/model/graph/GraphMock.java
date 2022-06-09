// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.graph;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class GraphMock {

    public static class BA extends ConfigModelBuilder<A> {
        public BA() { super(A.class); }
        @Override public List<ConfigModelId> handlesElements() { return Arrays.asList(); }
        @Override public void doBuild(A model, Element spec, ConfigModelContext modelContext) { }
    }
    public static class A extends ConfigModel {
        public A(ConfigModelContext modelContext) { super(modelContext); }
    }

    public static class BB extends ConfigModelBuilder<B> {
        public BB() { super(B.class); }
        @Override public List<ConfigModelId> handlesElements() { return Arrays.asList(); }
        @Override public void doBuild(B model, Element spec, ConfigModelContext modelContext) { }
    }
    public static class B extends ConfigModel {
        public final A a;
        @Inject
        public B(ConfigModelContext modelContext, A modelA) { super(modelContext); this.a = modelA; }
    }

    public static class BC extends ConfigModelBuilder<C> {
        public BC() { super(C.class); }
        @Override public List<ConfigModelId> handlesElements() { return Arrays.asList(); }
        @Override public void doBuild(C model, Element spec, ConfigModelContext modelContext) { }
    }
    public static class C extends ConfigModel {
        public Collection<B> b;
        public A a;
        public C(ConfigModelContext modelContext, Collection<B> modelB, A modelA) { super(modelContext); b = modelB; a = modelA; }
    }

    public static class BD extends ConfigModelBuilder<D> {
        public BD() { super(D.class); }
        @Override public List<ConfigModelId> handlesElements() { return Arrays.asList(); }
        @Override public void doBuild(D model, Element spec, ConfigModelContext modelContext) { }
    }
    public static class D extends ConfigModel {
        public D(ConfigModelContext modelContext, E modelE) { super(modelContext); }
    }

    public static class BE extends ConfigModelBuilder<E> {
        public BE() { super(E.class); }
        @Override public List<ConfigModelId> handlesElements() { return Arrays.asList(); }
        @Override public void doBuild(E model, Element spec, ConfigModelContext modelContext) { }
    }
    public static class E extends ConfigModel {
        public E(ConfigModelContext modelContext, D modelD) { super(modelContext); }
    }

    public static class Bad extends ConfigModel {
        public Bad() { super(null); }
        public static class Builder extends ConfigModelBuilder<Bad> {
            public Builder() { super(Bad.class); }
            @Override public List<ConfigModelId> handlesElements() { return null; }
            @Override public void doBuild(Bad model, Element spec, ConfigModelContext modelContext) { }
        }
    }

    public static class Bad2 extends ConfigModel {
        public Bad2(ConfigModelContext ctx, String foo) { super(ctx); }
        public static class Builder extends ConfigModelBuilder<Bad2> {
            public Builder() { super(Bad2.class); }
            @Override public List<ConfigModelId> handlesElements() { return null; }
            @Override public void doBuild(Bad2 model, Element spec, ConfigModelContext modelContext) { }
        }
    }
}
