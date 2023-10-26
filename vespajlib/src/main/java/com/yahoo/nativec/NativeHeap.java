// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.nativec;

import com.sun.jna.Platform;

/**
 * Represents the native C heap if accessible
 *
 * @author baldersheim
 */
public class NativeHeap {
    public long usedSize() { return 0; }
    public long totalSize() { return 0; }
    public long availableSize() { return 0; }
    public static NativeHeap sample() {
        if (Platform.isLinux()) {
            GLibcVersion version = new GLibcVersion();
            if ((version.major() >= 3) || ((version.major() == 2) && (version.minor() >= 33))) {
                return new MallInfo2();
            }
            return new MallInfo();
        }
        return new NativeHeap();
    }
}
