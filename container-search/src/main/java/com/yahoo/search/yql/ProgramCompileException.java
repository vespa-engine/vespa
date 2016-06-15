// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

class ProgramCompileException extends RuntimeException {

    private Location sourceLocation;

    public ProgramCompileException(String message) {
        super(message);
    }

    public ProgramCompileException(String message, Object... args) {
        super(formatMessage(message, args));
    }

    private static String formatMessage(String message, Object... args) {
        return args == null ? message : String.format(message, args);
    }

    public ProgramCompileException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProgramCompileException(Throwable cause) {
        super(cause);
    }

    public ProgramCompileException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }


    public ProgramCompileException(Location sourceLocation, String message, Object... args) {
        super(String.format("%s %s", sourceLocation != null ? sourceLocation : "", args == null ? message : String.format(message, args)));
        this.sourceLocation = sourceLocation;
    }

}
