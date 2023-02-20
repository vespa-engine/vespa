// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

/**
 * @author Tony Vaagenes
 */
public class FileReferenceDoesNotExistException extends RuntimeException {

    public final String fileReference;

    public FileReferenceDoesNotExistException(String fileReference) {
        super("Could not retrieve file with file reference '" + fileReference + "'");
        this.fileReference = fileReference;
    }

}
