package com.yahoo.nativec;

import com.sun.jna.Structure;

/**
 * Gives access to the information provided by the C library mallinfo2() function.
 *
 * @author baldersheim
 */
public class MallInfo2 extends NativeHeap {
    private final static Throwable initException = NativeC.loadLibrary(MallInfo2.class);
    public static Throwable init() {
        return initException;
    }

    @Structure.FieldOrder({"arena", "ordblks", "smblks", "hblks", "hblkhd", "usmblks", "fsmblks", "uordblks", "fordblks", "keepcost"})
    public static class MallInfo2Struct extends Structure {
        public static class ByValue extends MallInfo2Struct implements Structure.ByValue { }
        public long arena;     /* Non-mmapped space allocated (bytes) */
        public long ordblks;   /* Number of free chunks */
        public long smblks;    /* Number of free fastbin blocks */
        public long hblks;     /* Number of mmapped regions */
        public long hblkhd;    /* Space allocated in mmapped regions (bytes) */
        public long usmblks;   /* See below */
        public long fsmblks;   /* Space in freed fastbin blocks (bytes) */
        public long uordblks;  /* Total allocated space (bytes) */
        public long fordblks;  /* Total free space (bytes) */
        public long keepcost;  /* Top-most, releasable space (bytes) */
    }
    private static native MallInfo2Struct.ByValue mallinfo2();
    private final MallInfo2Struct mallinfo;

    public MallInfo2() {
        mallinfo = mallinfo2();
    }

    @Override
    public long usedSize() {
        return mallinfo.uordblks;
    }

    @Override
    public long totalSize() {
        return mallinfo.arena;
    }

    @Override
    public long availableSize() {
        return mallinfo.fordblks;
    }
}
