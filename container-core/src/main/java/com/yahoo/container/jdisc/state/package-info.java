// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
@ExportPackage
package com.yahoo.container.jdisc.state;

import com.yahoo.osgi.annotation.ExportPackage;

/**
 * Metrics implementation for jDisc. This consumes metrics over the jDisc metric API
 * and makes these available for in-process consumption through
 * {@link com.yahoo.container.jdisc.state.StateMonitor#snapshot},
 * and off-process through a jDisc handler.
 */
