// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.processing.IllegalInputException;

class ProgramCompileException extends IllegalInputException {

    private Location sourceLocation;

    public ProgramCompileException(String message) {
        super(message);
    }

    public ProgramCompileException(Location sourceLocation, String message, Object... args) {
        super(String.format("%s %s", sourceLocation != null ? sourceLocation : "", args == null ? message : String.format(message, args)));
        this.sourceLocation = sourceLocation;
    }

}
