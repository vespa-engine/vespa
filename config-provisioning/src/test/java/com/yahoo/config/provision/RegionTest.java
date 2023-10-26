// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author Ulf Lilleengen
 * @since 5.23
 */
public class RegionTest extends IdentifierTestBase<RegionName> {
    @Override
    protected RegionName createInstance(String id) {
        return RegionName.from(id);
    }

    @Override
    protected RegionName createDefaultInstance() {
        return RegionName.defaultName();
    }

    @Override
    protected boolean isDefault(RegionName instance) {
        return instance.isDefault();
    }
}
