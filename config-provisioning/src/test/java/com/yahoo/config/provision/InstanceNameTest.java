// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author Ulf Lilleengen
 * @since 5.25
 */
public class InstanceNameTest extends IdentifierTestBase<InstanceName> {
    @Override
    protected InstanceName createInstance(String id) {
        return InstanceName.from(id);
    }

    @Override
    protected InstanceName createDefaultInstance() {
        return InstanceName.defaultName();
    }

    @Override
    protected boolean isDefault(InstanceName instance) {
        return instance.isDefault();
    }
}
