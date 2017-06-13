// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * $Id$
 *
 */

package com.yahoo.logserver.formatter;

import com.yahoo.log.LogMessage;

/**
 * This formatter doesn't really format anything.  It just
 * calls the LogMessage toString() method.  This is kind of
 * pointless and silly, but we include it for symmetry...
 * or completeness....or...whatever.
 *
 * @author Bjorn Borud
 */
public class NullFormatter implements LogFormatter {

    public String format(LogMessage msg) {
        return msg.toString();
    }

    public String description() {
        return "Format message in native VESPA format";
    }

}
