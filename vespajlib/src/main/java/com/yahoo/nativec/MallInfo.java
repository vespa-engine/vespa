package com.yahoo.nativec;

import com.sun.jna.Structure;

public class MallInfo {
    private final static Throwable initException = NativeC.loadLibrary(MallInfo.class);
    public static Throwable init() {
        return initException;
    }

    @Structure.FieldOrder({"arena", "ordblks", "smblks", "hblks", "hblkhd", "usmblks", "fsmblks", "uordblks", "fordblks", "keepcost"})
    public static class MallInfoStruct extends Structure {
        public static class ByValue extends MallInfoStruct implements Structure.ByValue { }
        public int arena;     /* Non-mmapped space allocated (bytes) */
        public int ordblks;   /* Number of free chunks */
        public int smblks;    /* Number of free fastbin blocks */
        public int hblks;     /* Number of mmapped regions */
        public int hblkhd;    /* Space allocated in mmapped regions (bytes) */
        public int usmblks;   /* See below */
        public int fsmblks;   /* Space in freed fastbin blocks (bytes) */
        public int uordblks;  /* Total allocated space (bytes) */
        public int fordblks;  /* Total free space (bytes) */
        public int keepcost;  /* Top-most, releasable space (bytes) */
    }
    private static native MallInfoStruct.ByValue mallinfo();
    public MallInfo() {
        mallinfo = mallinfo();
    }
    private final MallInfoStruct mallinfo;
}
