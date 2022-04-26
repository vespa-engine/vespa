package com.yahoo.nativec;

import com.sun.jna.Native;
import com.sun.jna.Platform;

class NativeC {
    protected static Throwable loadLibrary() {
        if (Platform.isLinux()) {
            try {
                Native.register(Platform.C_LIBRARY_NAME);
            } catch (Throwable e) {
                return e;
            }
        } else {
            return new RuntimeException("Platform is unsúpported. Only supported on linux.");
        }
        return null;
    }

}
