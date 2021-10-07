// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.test.TotalOrderTester;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 * @since 5.26
 */
public class TenantTest extends IdentifierTestBase<TenantName> {
    @Override
    protected TenantName createInstance(String id) {
        return TenantName.from(id);
    }

    @Override
    protected TenantName createDefaultInstance() {
        return TenantName.defaultName();
    }

    @Override
    protected boolean isDefault(TenantName instance) {
        return instance.equals(TenantName.defaultName());
    }

    @Test
    public void testComparator() {
        assertThat(TenantName.defaultName().compareTo(TenantName.defaultName()), is(0));

        new TotalOrderTester<TenantName>()
                .theseObjects(TenantName.from("a"), TenantName.from("a"))
                .areLessThan(TenantName.from("b"))
                .testOrdering();
    }
}
