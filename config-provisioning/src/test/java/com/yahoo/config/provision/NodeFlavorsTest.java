// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.config.provisioning.FlavorsConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


public class NodeFlavorsTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void testReplacesWithBadValue() {
        FlavorsConfig.Builder builder = new FlavorsConfig.Builder();
        List<FlavorsConfig.Flavor.Builder> flavorBuilderList = new ArrayList<>();
        FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
        FlavorsConfig.Flavor.Replaces.Builder flavorReplacesBuilder = new FlavorsConfig.Flavor.Replaces.Builder();
        flavorReplacesBuilder.name("non-existing-config");
        flavorBuilder.name("strawberry").cost(2).replaces.add(flavorReplacesBuilder);
        flavorBuilderList.add(flavorBuilder);
        builder.flavor(flavorBuilderList);
        FlavorsConfig config = new FlavorsConfig(builder);
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Replaces for strawberry pointing to a non existing flavor: non-existing-config");
        new NodeFlavors(config);
    }

    @Test
    public void testConfigParsing() {
        FlavorsConfig.Builder builder = new FlavorsConfig.Builder();
        List<FlavorsConfig.Flavor.Builder> flavorBuilderList = new ArrayList<>();
        {
            FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
            FlavorsConfig.Flavor.Replaces.Builder flavorReplacesBuilder = new FlavorsConfig.Flavor.Replaces.Builder();
            flavorReplacesBuilder.name("banana");
            flavorBuilder.name("strawberry").cost(2).replaces.add(flavorReplacesBuilder);
            flavorBuilderList.add(flavorBuilder);
        }
        {
            FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
            flavorBuilder.name("banana").cost(3);
            flavorBuilderList.add(flavorBuilder);
        }
        builder.flavor(flavorBuilderList);
        FlavorsConfig config = new FlavorsConfig(builder);
        NodeFlavors nodeFlavors = new NodeFlavors(config);
        assertThat(nodeFlavors.getFlavor("banana").get().cost(), is(3));
    }

    @Test
    public void testHasAtLeast() {
        Flavor flavor = new Flavor(new NodeResources(1, 2, 3));
        assertTrue(flavor.hasAtLeast(new NodeResources(1, 2, 3)));
        assertTrue(flavor.hasAtLeast(new NodeResources(1, 1.5, 2)));
        assertFalse(flavor.hasAtLeast(new NodeResources(1, 1.5, 4)));
        assertFalse(flavor.hasAtLeast(new NodeResources(2, 1.5, 4)));
        assertFalse(flavor.hasAtLeast(new NodeResources(1, 2.1, 4)));
    }

    @Test
    public void testRetiredFlavorWithoutReplacement() {
        FlavorsConfig.Builder builder = new FlavorsConfig.Builder();
        List<FlavorsConfig.Flavor.Builder> flavorBuilderList = new ArrayList<>();
        {
            FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
            flavorBuilder.name("retired").retired(true);
            flavorBuilderList.add(flavorBuilder);
        }
        {
            FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
            flavorBuilder.name("chocolate");
            flavorBuilderList.add(flavorBuilder);
        }
        builder.flavor(flavorBuilderList);
        FlavorsConfig config = new FlavorsConfig(builder);
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Flavor 'retired' is retired, but has no replacement");
        new NodeFlavors(config);
    }

}
