// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi;

import com.yahoo.component.ComponentSpecification;
import org.osgi.framework.Bundle;

/**
 * @author gjoranv
 */
public interface OsgiWrapper extends Osgi, com.yahoo.container.di.Osgi {

    @Override
    default Bundle getBundle(ComponentSpecification bundleId) {
        return null;
    }

}
