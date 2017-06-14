// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle.l;

import java.util.concurrent.Callable;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class CertificateL implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return 1;
    }
}
