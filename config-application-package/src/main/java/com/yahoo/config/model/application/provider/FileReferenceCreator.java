// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.FileReference;

import java.lang.reflect.Constructor;

/**
 * Convenience for creating a {@link com.yahoo.config.FileReference}.
 *
 * @author gjoranv
 */
public class FileReferenceCreator {

    public static FileReference create(String stringVal) {
        try {
            Constructor<FileReference> ctor = FileReference.class.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(stringVal);
        } catch (Exception e) {
            throw new RuntimeException("Could not create a new " + FileReference.class.getName() +
                    ". This should never happen!", e);
        }
    }

}
