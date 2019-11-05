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
