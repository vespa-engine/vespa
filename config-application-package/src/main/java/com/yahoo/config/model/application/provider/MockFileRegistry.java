// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A file registry for testing, and, it seems, doubling as a null registry in some code paths.
 *
 * @author tonytv
 */
public class MockFileRegistry implements FileRegistry {

    public FileReference addFile(String relativePath) {
        return new FileReference("0123456789abcdef");
    }

    @Override
    public String fileSourceHost() {
        return "localhost.fortestingpurposesonly";
    }

    public static final Entry entry1 = new Entry("component/path1", new FileReference("1234"));
    public static final Entry entry2 = new Entry("component/path2", new FileReference("56789"));

    public List<Entry> export() {
        List<Entry> result = new ArrayList<>();
        result.add(entry1);
        result.add(entry2);
        return result;
    }

    @Override
    public FileReference addUri(String uri) {
        throw new IllegalArgumentException("FileReference addUri(String uri) is not implemented for " + getClass().getCanonicalName());
    }

}
