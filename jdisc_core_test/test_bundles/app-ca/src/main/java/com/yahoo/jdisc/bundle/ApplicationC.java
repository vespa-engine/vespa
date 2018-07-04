// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.bundle.a.CertificateA;

/**
 * @author Simon Thoresen Hult
 */
public class ApplicationC implements Application {

    private final CertificateA certificateA = new CertificateA();

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
