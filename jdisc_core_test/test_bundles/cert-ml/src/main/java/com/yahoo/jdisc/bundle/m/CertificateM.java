// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle.m;

import java.util.concurrent.Callable;

/**
 * @author Simon Thoresen Hult
 */
public class CertificateM implements Callable<Integer> {

    @Override
    @SuppressWarnings({ "unchecked" })
    public Integer call() throws Exception {
        Class<?> certClass = Class.forName("com.yahoo.jdisc.bundle.l.CertificateL");
        Callable<Integer> cert = (Callable<Integer>)certClass.getDeclaredConstructor().newInstance();
        return cert.call();
    }
}
