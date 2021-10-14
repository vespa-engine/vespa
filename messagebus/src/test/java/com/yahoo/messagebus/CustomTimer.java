// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.Timer;

/**
 * @author <a href="mailto:thomasg@yahoo-inc.com">Thomas Gundersen</a>
 */
class CustomTimer implements Timer {

    long millis = 0;

    @Override
    public long milliTime() {
        return millis;
    }
}
