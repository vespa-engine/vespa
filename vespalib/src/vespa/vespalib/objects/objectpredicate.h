// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

class Identifiable;

/**
 * A predicate that is able to say either true or false when presented
 * with a generic object.
 **/
class ObjectPredicate
{
public:
    /**
     * Apply this predicate to the given object.
     *
     * @return true or false
     * @param obj the object to check
     **/
    virtual bool check(const Identifiable &obj) const = 0;

    /**
     * empty
     **/
    virtual ~ObjectPredicate() { }
};

} // namespace vespalib

