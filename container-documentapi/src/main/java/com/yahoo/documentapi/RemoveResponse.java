// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * This response is provided for successful document remove operations. Use the
 * wasFound() method to check whether or not the document was actually found.
 *
 * @author Simon Thoresen Hult
 */
public class RemoveResponse extends Response {

    private final boolean wasFound;

    public RemoveResponse(long requestId, boolean wasFound) {
        super(requestId);
        this.wasFound = wasFound;
    }

    public boolean wasFound() {
        return wasFound;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Boolean.valueOf(wasFound).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RemoveResponse)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        RemoveResponse rhs = (RemoveResponse)obj;
        if (wasFound != rhs.wasFound) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Remove" + super.toString() + " " + wasFound;
    }
}
