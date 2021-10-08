// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.logging.Logger;

import org.junit.Test;

import com.yahoo.container.StatisticsConfig;

/**
 * Check register/remove semantics.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class StatisticsImplTestCase {
    private static class TestHandle extends Handle {

        TestHandle(final String name, final Statistics manager,
                final Callback parametrizedCallback) {
            super(name, manager, parametrizedCallback);
        }

        @Override
        public void runHandle() {
        }

        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }

    }

    @Test
    public final void testRegister() {
        final StatisticsConfig config = new StatisticsConfig(
                new StatisticsConfig.Builder().collectionintervalsec(31e9)
                        .loggingintervalsec(31e9));
        final StatisticsImpl s = new StatisticsImpl(config);
        final Logger logger = Logger.getLogger(TestHandle.class.getName());
        final boolean initUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        final String firstHandle = "a";
        final Handle a = new TestHandle(firstHandle, s, null);
        final Handle a2 = new TestHandle(firstHandle, s, null);
        final String secondHandle = "b";
        final Handle b = new TestHandle(secondHandle, s, null);
        s.register(a);
        s.register(a2);
        assertFalse("Old handle should be cancelled.", a.isCancelled() == false);
        assertFalse("New handle should not be cancelled.", a2.isCancelled());
        assertEquals("Internal handles map semantics have been changed?", 1,
                s.handles.size());
        s.register(b);
        s.remove(secondHandle);
        assertFalse("Removed handle should be cancelled.",
                b.isCancelled() == false);
        a2.cancel();
        s.purge();
        assertEquals("Cancelled tasks should be removed.", 0, s.handles.size());
        s.deconstruct();
        assertSame(config, s.getConfig());
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    @Test
    public void freezeNullImplementationBehavior() {
        Statistics s = Statistics.nullImplementation;
        assertEquals(0, s.purge());
        // invoke s.register
        Handle h = new Handle("nalle", s, null) {
            @Override
            public void runHandle() {
            }

            @Override
            public boolean equals(Object o) {
                return true;
            }

            @Override
            public int hashCode() {
                return 0;
            }

        };
        assertEquals(0, s.purge());
        s.register(h);
        s.remove("nalle");
        s.register(h);
        h.cancel();
        assertEquals(0, s.purge());
    }

}
