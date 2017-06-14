// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author lulf
 * @since 5.25
 */
public class ApplicationNameTest extends IdentifierTestBase<ApplicationName> {
    @Override
    protected ApplicationName createInstance(String id) {
        return ApplicationName.from(id);
    }

    @Override
    protected ApplicationName createDefaultInstance() {
        return ApplicationName.defaultName();
    }

    @Override
    protected boolean isDefault(ApplicationName instance) {
        return instance.isDefault();
    }
}
