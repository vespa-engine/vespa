package com.yahoo.container.plugin.classanalysis.sampleclasses;

import java.util.ArrayList;
import java.util.List;

/**
 * Input for class analysis test verifying Java 15 records,
 * plus Java 16 pattern matching for instanceof.
 *
 * @author gjoranv
 */
public record RecordWithOverride(List<Byte> list) {

    public RecordWithOverride {
        if (list instanceof ArrayList<Byte> l) {
            throw new IllegalArgumentException(l.toString());
        }
    }
}
