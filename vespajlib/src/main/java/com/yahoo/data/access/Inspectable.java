// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access;

/**
 * Minimal API to implement for objects containing or exposing
 * structured, generic, schemaless data.  Use this when it's
 * impractical to implement the Inspector interface directly.
 */
public interface Inspectable {

    /** Returns an Inspector exposing this object's structured data. */
    Inspector inspect();

}
