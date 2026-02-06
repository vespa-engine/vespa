package org.apache.datasketches.memory;

import java.lang.foreign.MemorySegment;

public class Memory {
	public static MemorySegment wrap(byte[] b) {
		return MemorySegment.ofArray(b);
	}
}
