// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.PortAllocBridge;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StartupCommandChangeValidatorTest {

    @Test
    void requireThatDifferentStartupCommandIsDetected() {
        MockRoot oldRoot = createRootWithChildren(new ServiceStub("evilservice", "rm -rf /"));
        MockRoot newRoot = createRootWithChildren(new ServiceStub("evilservice", "rm -rf *"));
        List<ConfigChangeAction> changes = getStartupCommandChanges(oldRoot, newRoot);
        assertEquals(1, changes.size());
        assertEquals("evilservice", changes.get(0).getServices().get(0).getConfigId());
    }

    @Test
    void requireEmptyResultForEqualStartupCommand() {
        MockRoot oldRoot = createRootWithChildren(new ServiceStub("evilservice", "./hax.sh"));
        MockRoot newRoot = createRootWithChildren(new ServiceStub("evilservice", "./hax.sh"));
        List<ConfigChangeAction> changes = getStartupCommandChanges(oldRoot, newRoot);
        assertTrue(changes.isEmpty());
    }

    @Test
    void requireEmptyResultForDifferentServices() {
        MockRoot oldRoot = createRootWithChildren(new ServiceStub("evilservice", "./hax.sh"));
        MockRoot newRoot = createRootWithChildren(new ServiceStub("goodservice", "./hax.sh"));
        List<ConfigChangeAction> changes = getStartupCommandChanges(oldRoot, newRoot);
        assertTrue(changes.isEmpty());
    }

    private static List<ConfigChangeAction> getStartupCommandChanges(
            AbstractConfigProducerRoot currentModel, AbstractConfigProducerRoot nextModel) {
        StartupCommandChangeValidator validator = new StartupCommandChangeValidator();
        return validator.findServicesWithChangedStartupCommand(currentModel, nextModel).collect(Collectors.toList());
    }

    private static MockRoot createRootWithChildren(AbstractConfigProducer<?>... children) {
        MockRoot root = new MockRoot();
        Arrays.asList(children).forEach(root::addChild);
        root.freezeModelTopology();
        return root;
    }

    private static class ServiceStub extends AbstractService {
        private final String startupCommand;

        public ServiceStub(String name, String startupCommand) {
            super(name);
            setHostResource(new HostResource(new Host(null, "localhost")));
            this.startupCommand = startupCommand;
        }

        @Override
        public Optional<String> getStartupCommand() {
            return Optional.ofNullable(startupCommand);
        }

        @Override
        public int getPortCount() {
            return 0;
        }

        @Override public void allocatePorts(int start, PortAllocBridge from) { }
    }
}
