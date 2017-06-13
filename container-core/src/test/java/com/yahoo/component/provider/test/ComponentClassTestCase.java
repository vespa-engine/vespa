// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.provider.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import com.yahoo.component.AbstractComponent;
import org.junit.Test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.Version;
import com.yahoo.component.provider.ComponentClass;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.core.IntConfig;
import com.yahoo.config.core.StringConfig;
import com.yahoo.vespa.config.ConfigKey;

/**
 * @author <a href="gv@yahoo-inc.com">G. Voldengen</a>
 */
@SuppressWarnings("unused")
public class ComponentClassTestCase {

    @Test
    public void testComponentConstructor() throws NoSuchMethodException {
        ComponentClass<A> a = new ComponentClass<>(A.class);
        assertEquals(A.preferred(), a.getPreferredConstructor().getConstructor());

        ComponentClass<B> b = new ComponentClass<>(B.class);
        assertEquals(B.preferred(), b.getPreferredConstructor().getConstructor());

        ComponentClass<C> c = new ComponentClass<>(C.class);
        assertEquals(C.preferred(), c.getPreferredConstructor().getConstructor());

        ComponentClass<E> e = new ComponentClass<>(E.class);
        assertEquals(E.preferred(), e.getPreferredConstructor().getConstructor());

        ComponentClass<G> g = new ComponentClass<>(G.class);
        assertEquals(G.preferred(), g.getPreferredConstructor().getConstructor());

        try {
            ComponentClass<H> h = new ComponentClass<>(H.class);
            fail("Expected exception due to no legal public constructors.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must have at least one public constructor with an optional " +
                  "component ID followed by an optional FileAcquirer and zero or more config arguments"));
        }

        try {
            ComponentClass<I> i = new ComponentClass<>(I.class);
            fail("Expected exception due to no public constructors.");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Class has no public constructors"));
        }

        try {
            ComponentClass<J> j = new ComponentClass<>(J.class);
            fail("Expected exception due to no public constructors.");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Class has no public constructors"));
        }

        ComponentClass<K> k = new ComponentClass<>(K.class);
        assertEquals(K.preferred(), k.getPreferredConstructor().getConstructor());

        ComponentClass<L> l = new ComponentClass<>(L.class);
        assertEquals(L.preferred(), l.getPreferredConstructor().getConstructor());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateComponent() throws NoSuchMethodException {
        Map<ConfigKey, ConfigInstance> availableConfigs = new HashMap<>();
        String configId = "testConfigId";
        availableConfigs.put(new ConfigKey(StringConfig.class, configId), new StringConfig(new StringConfig.Builder()));
        availableConfigs.put(new ConfigKey(IntConfig.class, configId), new IntConfig(new IntConfig.Builder()));

        ComponentClass<TestComponent> testClass = new ComponentClass<>(TestComponent.class);
        TestComponent component = testClass.
                createComponent(new ComponentId("test", new Version(1)), availableConfigs, configId);
        assertEquals("test", component.getId().getName());
        assertEquals(1, component.getId().getVersion().getMajor());
        assertEquals(1, component.intVal);
        assertEquals("_default_", component.stringVal);
    }

    /**
     * Verifies that ComponentClass sets the ComponentId when a component that takes a ComponentId as
     * constructor argument fails to call super(id).
     */
    @Test
    public void testNullIdComponent() throws NoSuchMethodException {
        ComponentClass<NullIdComponent> testClass = new ComponentClass<>(NullIdComponent.class);
        NullIdComponent component = testClass.createComponent(new ComponentId("null-test", new Version(1)), new HashMap<ConfigKey, ConfigInstance>(), null);
        assertEquals("null-test", component.getId().getName());
        assertEquals(1, component.getId().getVersion().getMajor());
    }

    public static class TestComponent extends AbstractComponent {
        private int intVal = 0;
        private String stringVal = "";
        public TestComponent(ComponentId id, IntConfig intConfig, StringConfig stringConfig) {
            super(id);
            intVal = intConfig.intVal();
            stringVal = stringConfig.stringVal();
        }
    }

    /**
     * This component takes a ComponentId as constructor arg, but "forgets" to call super(id).
     */
    public static class NullIdComponent extends AbstractComponent {
        private int intVal = 0;
        private String stringVal = "";
        public NullIdComponent(ComponentId id) {
        }
    }

    private static class A extends AbstractComponent {
        public A(IntConfig intConfig) { }
        public A(IntConfig intConfig, StringConfig stringConfig) { }
        static Constructor<A> preferred() throws NoSuchMethodException{
            return A.class.getConstructor(IntConfig.class, StringConfig.class);
        }
    }

    private static class B extends AbstractComponent {
        public B(ComponentId id, IntConfig intConfig) { }
        public B(IntConfig intConfig) { }
        static Constructor<B> preferred() throws NoSuchMethodException{
            return B.class.getConstructor(ComponentId.class, IntConfig.class);
        }
    }

    private static class C extends AbstractComponent {
        public C(IntConfig intConfig, ComponentId id) { }
        public C(String id, IntConfig intConfig) { }
        static Constructor<C> preferred() throws NoSuchMethodException{
            return C.class.getConstructor(IntConfig.class, ComponentId.class);
        }
    }

    private static class E extends AbstractComponent {
        public E(IntConfig intConfig) { }
        public E(String id, String illegal, IntConfig intConfig, StringConfig stringConfig) { }
        static Constructor<E> preferred() throws NoSuchMethodException{
            return E.class.getConstructor(IntConfig.class);
        }
    }

    private static class G extends AbstractComponent {
        public G(ComponentId id) { }
        public G(String id) { }
        static Constructor<G> preferred() throws NoSuchMethodException{
            return G.class.getConstructor(ComponentId.class);
        }
    }

    private static class H extends AbstractComponent {
        public H(ComponentId id, String illegal) { }
        public H(String id, String illegal) { }
    }

    private static class I extends AbstractComponent {
        protected I(ComponentId id) { }
    }

    private static class J extends AbstractComponent {
    }

    private static class K extends AbstractComponent {
        public K() { }
        public K(ComponentId id, String illegal) { }
        static Constructor<K> preferred() throws NoSuchMethodException{
            return K.class.getConstructor();
        }
    }

    private static class L extends AbstractComponent {
        public L(long l, long ll, long lll) { }
        public L(ComponentId id, IntConfig intConfig) { }
        static Constructor<L> preferred() throws NoSuchMethodException{
            return L.class.getConstructor(ComponentId.class, IntConfig.class);
        }
    }
}
