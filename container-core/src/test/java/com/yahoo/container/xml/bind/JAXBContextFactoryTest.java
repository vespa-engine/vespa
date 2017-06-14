// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.bind;

import com.yahoo.container.xml.providers.JAXBContextFactoryProvider;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author einarmr
 * @author gjoranv
 * @since 5.3
 */
public class JAXBContextFactoryTest {
    @Test
    public void testInstantiationAndDestruction() {

        JAXBContextFactoryProvider provider = new JAXBContextFactoryProvider();
        JAXBContextFactory factory = provider.get();
        assertThat(factory.getClass().getName(), equalTo(JAXBContextFactoryProvider.FACTORY_CLASS));

        try {
            JAXBContextFactory.getContextPath((Class) null);
            fail("Should have failed with null classes.");
        } catch (Exception e) { }

        try {
            JAXBContextFactory.getContextPath();
            fail("Should have failed with empty list.");
        } catch (Exception e) { }

        assertThat(JAXBContextFactory.getContextPath(this.getClass()),
                equalTo(this.getClass().getPackage().getName()));

        assertThat(JAXBContextFactory.getContextPath(this.getClass(),
                String.class),
                equalTo(this.getClass().getPackage().getName() + ":" +
                        String.class.getPackage().getName()));

        provider.deconstruct();

    }
}
