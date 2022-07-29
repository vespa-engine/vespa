// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class ConfigModelBuilderTest {
    @Test
    void testEquals() {
        ConfigModelBuilder<?> ba = new A.Builder();
        ConfigModelBuilder<?> ba2 = new A2.Builder();
        ConfigModelBuilder<?> bb = new B.Builder();
        ConfigModelBuilder<?> bb2 = new B2.Builder();

        assertEquals(ba, ba);
        assertEquals(ba, ba2);
        assertNotEquals(ba, bb);
        assertNotEquals(ba, bb2);

        assertEquals(ba2, ba);
        assertEquals(ba2, ba2);
        assertNotEquals(ba2, bb);
        assertNotEquals(ba2, bb2);

        assertNotEquals(bb, ba);
        assertNotEquals(bb, ba2);
        assertEquals(bb, bb);
        assertNotEquals(bb, bb2);

        assertNotEquals(bb2, ba);
        assertNotEquals(bb2, ba2);
        assertNotEquals(bb2, bb);
        assertEquals(bb2, bb2);

        assertNotEquals(ba, new ArrayList<>());
    }

    private static class A extends ConfigModel {
        public A(ConfigModelContext modelContext) { super(modelContext); }
        public static class Builder extends ConfigModelBuilder<A> {
            public Builder() { super(A.class); }

            @Override
            public List<ConfigModelId> handlesElements() {
                List<ConfigModelId> ids = new ArrayList<>();
                ids.add(ConfigModelId.fromName("foo"));
                ids.add(ConfigModelId.fromName("bar"));
                return ids;
            }

            @Override
            public void doBuild(A model, Element spec, ConfigModelContext modelContext) { }
        }

    }

    private static class A2 extends ConfigModel {
        public A2(ConfigModelContext modelContext) { super(modelContext); }
        public static class Builder extends ConfigModelBuilder<A2> {
            public Builder() { super(A2.class); }

            @Override
            public List<ConfigModelId> handlesElements() {
                List<ConfigModelId> ids = new ArrayList<>();
                ids.add(ConfigModelId.fromName("foo"));
                ids.add(ConfigModelId.fromName("bar"));
                return ids;
            }

            @Override
            public void doBuild(A2 model, Element spec, ConfigModelContext modelContext) { }
        }
    }

    private static class B extends ConfigModel {
        public B(ConfigModelContext modelContext) { super(modelContext); }
        public static class Builder extends ConfigModelBuilder<B> {
            public Builder() { super(B.class); }

            @Override
            public List<ConfigModelId> handlesElements() {
                List<ConfigModelId> ids = new ArrayList<>();
                ids.add(ConfigModelId.fromName("bar"));
                return ids;
            }

            @Override
            public void doBuild(B model, Element spec, ConfigModelContext modelContext) { }
        }
    }

    private static class B2 extends ConfigModel {
        public B2(ConfigModelContext modelContext) { super(modelContext); }
        public static class Builder extends ConfigModelBuilder<B2> {
            public Builder() { super(B2.class); }

            @Override
            public List<ConfigModelId> handlesElements() {
                List<ConfigModelId> ids = new ArrayList<>();
                ids.add(ConfigModelId.fromName("foo"));
                ids.add(ConfigModelId.fromName("bim"));
                return ids;
            }

            @Override
            public void doBuild(B2 model, Element spec, ConfigModelContext modelContext) { }
        }
    }
}
