// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class GuiceRepositoryTestCase {

    @Test
    void requireThatInstallWorks() {
        GuiceRepository guice = new GuiceRepository();
        StringBinding module = new StringBinding("fooKey", "fooVal");
        guice.install(module);
        assertBinding(guice, "fooKey", "fooVal");

        Iterator<Module> it = guice.iterator();
        assertTrue(it.hasNext());
        assertSame(module, it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatInstallAllWorks() {
        GuiceRepository guice = new GuiceRepository();
        StringBinding foo = new StringBinding("fooKey", "fooVal");
        StringBinding bar = new StringBinding("barKey", "barVal");
        guice.installAll(Arrays.asList(foo, bar));
        assertBinding(guice, "fooKey", "fooVal");
        assertBinding(guice, "barKey", "barVal");

        Iterator<Module> it = guice.iterator();
        assertTrue(it.hasNext());
        assertSame(foo, it.next());
        assertTrue(it.hasNext());
        assertSame(bar, it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatUninstallWorks() {
        GuiceRepository guice = new GuiceRepository();
        StringBinding module = new StringBinding("fooKey", "fooVal");
        guice.install(module);
        assertBinding(guice, "fooKey", "fooVal");

        guice.uninstall(module);
        assertNoBinding(guice, "fooKey");
        assertFalse(guice.iterator().hasNext());
    }

    @Test
    void requireThatUninstallAllWorks() {
        GuiceRepository guice = new GuiceRepository();
        StringBinding foo = new StringBinding("fooKey", "fooVal");
        StringBinding bar = new StringBinding("barKey", "barVal");
        StringBinding baz = new StringBinding("bazKey", "bazVal");
        guice.installAll(Arrays.asList(foo, bar, baz));
        assertBinding(guice, "fooKey", "fooVal");
        assertBinding(guice, "barKey", "barVal");
        assertBinding(guice, "bazKey", "bazVal");

        guice.uninstallAll(Arrays.asList(foo, baz));
        assertNoBinding(guice, "fooKey");
        assertBinding(guice, "barKey", "barVal");
        assertNoBinding(guice, "bazKey");

        Iterator<Module> it = guice.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertSame(bar, it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatBindingsCanBeOverridden() {
        GuiceRepository guice = new GuiceRepository();
        guice.install(new StringBinding("fooKey", "fooVal1"));
        assertBinding(guice, "fooKey", "fooVal1");
        guice.install(new StringBinding("fooKey", "fooVal2"));
        assertBinding(guice, "fooKey", "fooVal2");
    }

    @Test
    void requireThatModulesAreOnlyEvaluatedOnce() {
        GuiceRepository guice = new GuiceRepository();
        EvalCounter foo = new EvalCounter();
        EvalCounter bar = new EvalCounter();
        assertEquals(0, foo.cnt);
        assertEquals(0, bar.cnt);
        guice.install(foo);
        assertEquals(1, foo.cnt);
        assertEquals(0, bar.cnt);
        guice.install(bar);
        assertEquals(1, foo.cnt);
        assertEquals(1, bar.cnt);
    }

    @Test
    void requireThatPrivateModulesWorks() {
        GuiceRepository guice = new GuiceRepository();

        List<Named> names = Arrays.asList(Names.named("A"), Names.named("B"));

        for (Named name : names) {
            guice.install(createPrivateInjectNameModule(name));
        }

        Injector injector = guice.getInjector();

        for (Named name : names) {
            NameHolder nameHolder = injector.getInstance(Key.get(NameHolder.class, name));
            assertEquals(name, nameHolder.name);
        }
    }

    private Module createPrivateInjectNameModule(final Named name) {
        return new PrivateModule() {
            @Override
            protected void configure() {
                bind(NameHolder.class).annotatedWith(name).to(NameHolder.class);
                expose(NameHolder.class).annotatedWith(name);
                bind(Named.class).toInstance(name);
            }
        };
    }

    private static void assertBinding(GuiceRepository guice, String name, String expected) {
        assertEquals(expected, guice.getInjector().getInstance(Key.get(String.class, Names.named(name))));
    }

    private static void assertNoBinding(GuiceRepository guice, String name) {
        try {
            guice.getInjector().getInstance(Key.get(String.class, Names.named(name)));
            fail();
        } catch (ConfigurationException e) {

        }
    }

    private static class EvalCounter extends AbstractModule {

        int cnt = 0;

        @Override
        protected void configure() {
            ++cnt;
        }
    }

    private static class StringBinding extends AbstractModule {

        final String name;
        final String val;

        StringBinding(String name, String val) {
            this.name = name;
            this.val = val;
        }

        @Override
        protected void configure() {
            bind(String.class).annotatedWith(Names.named(name)).toInstance(val);
        }
    }

    public static final class NameHolder {
        public final Named name;

        @Inject
        public NameHolder(Named name) {
            this.name = name;
        }
    }
}
