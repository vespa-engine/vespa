// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.bundle.MockBundle;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.SharedResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class DeconstructorTest {
    public static Deconstructor deconstructor;

    @BeforeEach
    public void init() {
        deconstructor = new Deconstructor();
    }

    @Test
    void deconstructor_waits_for_completion_on_shutdown() {
        deconstructor = new Deconstructor();

        var slowDeconstructComponent = new SlowDeconstructComponent();
        deconstructor.deconstruct(0, List.of(slowDeconstructComponent), emptyList());
        deconstructor.shutdown();
        assertTrue(slowDeconstructComponent.destructed);
    }

    @Test
    void require_abstract_component_destructed() throws InterruptedException {
        TestAbstractComponent abstractComponent = new TestAbstractComponent();
        deconstructor.deconstruct(0, List.of(abstractComponent), emptyList());

        waitForDeconstructToComplete(() -> abstractComponent.destructed);
        assertTrue(abstractComponent.destructed);
    }

    @Test
    void require_provider_destructed() throws InterruptedException {
        TestProvider provider = new TestProvider();
        deconstructor.deconstruct(0, List.of(provider), emptyList());

        waitForDeconstructToComplete(() -> provider.destructed);
        assertTrue(provider.destructed);
    }

    @Test
    void require_shared_resource_released() throws InterruptedException {
        TestSharedResource sharedResource = new TestSharedResource();
        deconstructor.deconstruct(0, List.of(sharedResource), emptyList());
        waitForDeconstructToComplete(() -> sharedResource.released);
        assertTrue(sharedResource.released);
    }

    @Test
    void bundles_are_uninstalled() throws InterruptedException {
        var bundle = new UninstallableMockBundle();
        // Done by executor, so it takes some time even with a 0 delay.
        deconstructor.deconstruct(0, emptyList(), singleton(bundle));

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
