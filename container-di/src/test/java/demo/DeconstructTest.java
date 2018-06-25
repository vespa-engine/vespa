// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package demo;

import com.yahoo.container.di.ContainerTest;
import com.yahoo.container.di.ContainerTestBase;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author tonytv
 * @author gjoranv
 */
public class DeconstructTest extends ContainerTestBase  {
    public static class DeconstructableComponent extends ContainerTest.DestructableComponent {
        private boolean isDeconstructed = false;

        @Override
        public void deconstruct() {
            isDeconstructed = true;
        }
    }

    @Test
    public void require_that_unused_components_are_deconstructed() {
        writeBootstrapConfigs("d1", DeconstructableComponent.class);
        complete();

        DeconstructableComponent d1 = getInstance(DeconstructableComponent.class);

        writeBootstrapConfigs("d2", DeconstructableComponent.class);
        complete();

        assertTrue(d1.isDeconstructed);
    }
}
