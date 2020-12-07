// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import org.osgi.framework.Bundle;

import java.util.Collection;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public interface ComponentDeconstructor {

    void deconstruct(Collection<Object> components, Collection<Bundle> bundles);

}
