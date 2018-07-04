// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.google.inject.Inject;
import com.yahoo.jdisc.application.AbstractApplication;
import com.yahoo.jdisc.application.BundleInstaller;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.service.CurrentContainer;

/**
 * @author Simon Thoresen Hult
 */
public class ApplicationH extends AbstractApplication {

    @Inject
    public ApplicationH(BundleInstaller bundleInstaller, ContainerActivator activator, CurrentContainer container) {
        super(bundleInstaller, activator, container);
    }

    @Override
    public void start() {
        org.apache.commons.logging.LogFactory.getLog("jcl").info("[jcl] hello world");
        org.apache.log4j.Logger.getLogger("log4j").info("[log4j] hello world");
        org.slf4j.LoggerFactory.getLogger("slf4j").info("[slf4j] hello world");
        java.util.logging.Logger.getLogger("jdk14").info("[jdk14] hello world");
    }
}
