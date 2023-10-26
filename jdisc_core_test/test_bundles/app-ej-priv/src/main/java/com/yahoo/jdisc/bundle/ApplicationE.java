// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.bundle.j.CertificateJ;

/**
 * @author Simon Thoresen Hult
 */
public class ApplicationE implements Application {

    private final CertificateJ certificateJ = new CertificateJ();

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void destroy() {

    }
}
