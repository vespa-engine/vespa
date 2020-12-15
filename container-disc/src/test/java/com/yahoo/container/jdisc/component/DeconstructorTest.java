// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.bundle.MockBundle;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.SharedResource;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class DeconstructorTest {
    public static Deconstructor deconstructor;

    @Before
    public void init() {
        deconstructor = new Deconstructor(Deconstructor.Mode.RECONFIG, Duration.ZERO);
    }

    @Test
    public void deconstruct_is_synchronous_in_shutdown_mode() {
        deconstructor = new Deconstructor(Deconstructor.Mode.SHUTDOWN);

        var slowDeconstructComponent = new SlowDeconstructComponent();
        deconstructor.deconstruct(List.of(slowDeconstructComponent), emptyList());
        assertTrue(slowDeconstructComponent.destructed);
    }

    @Test
    public void require_abstract_component_destructed() throws InterruptedException {
        TestAbstractComponent abstractComponent = new TestAbstractComponent();
        deconstructor.deconstruct(List.of(abstractComponent), emptyList());

        waitForDeconstructToComplete(() -> abstractComponent.destructed);
        assertTrue(abstractComponent.destructed);
    }

    @Test
    public void require_provider_destructed() throws InterruptedException {
        TestProvider provider = new TestProvider();
        deconstructor.deconstruct(List.of(provider), emptyList());

        waitForDeconstructToComplete(() -> provider.destructed);
        assertTrue(provider.destructed);
    }

    @Test
    public void require_shared_resource_released() {
        TestSharedResource sharedResource = new TestSharedResource();
        deconstructor.deconstruct(List.of(sharedResource), emptyList());
        assertTrue(sharedResource.released);
    }

    @Test
    public void bundles_are_uninstalled() throws InterruptedException {
        var bundle = new UninstallableMockBundle();
        // Done by executor, so it takes some time even with a 0 delay.
        deconstructor.deconstruct(emptyList(), singleton(bundle));

        waitForDeconstructToComplete(() -> bundle.uninstalled);
        assertTrue(bundle.uninstalled);
    }

    // Deconstruct is async in RECONFIG mode, so must wait even with a zero delay.
    private void waitForDeconstructToComplete(Supplier<Boolean> destructed) throws InterruptedException {
        var end = Instant.now().plusSeconds(30);
        while (! destructed.get() && Instant.now().isBefore(end)) {
            Thread.sleep(10);
        }
    }

    private static class TestAbstractComponent extends AbstractComponent {
        boolean destructed = false;
        @Override public void deconstruct() { destructed = true; }
    }

    private static class SlowDeconstructComponent extends AbstractComponent {
        boolean destructed = false;
        @Override
        public void deconstruct() {
            // Add delay to verify that the Deconstructor waits until this is complete before returning.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("The delayed deconstruct was interrupted.");
            }
            destructed = true;
        }
    }

    private static class TestProvider implements Provider<Void> {
        volatile boolean destructed = false;

        @Override public Void get() { return null; }
        @Override public void deconstruct() { destructed = true; }
    }

    private static class TestSharedResource implements SharedResource {
        volatile boolean released = false;

        @Override public ResourceReference refer() { return null; }
        @Override public void release() { released = true; }
    }

    private static class UninstallableMockBundle extends MockBundle {
        boolean uninstalled = false;
        @Override public void uninstall() {
            uninstalled = true;
        }
    }

}
