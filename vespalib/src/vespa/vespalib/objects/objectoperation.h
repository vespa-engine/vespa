// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

class Identifiable;

/**
 * An operation that is able to operate on a generic object.
 **/
class ObjectOperation
{
public:
    /**
     * Apply this operation to the given object.
     *
     * @param obj the object to operate on
     **/
    virtual void execute(Identifiable &obj) = 0;

    /**
     * empty
     **/
    virtual ~ObjectOperation() { }
};

} // namespace vespalib

