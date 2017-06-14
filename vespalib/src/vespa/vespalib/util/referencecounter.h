// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file referencecounter.h
 * @author Thomas F. Gundersen
 * @version $Id$
 * @date 2004-03-19
 **/

#pragma once

#include <vespa/vespalib/util/atomic.h>
#include <assert.h>

namespace vespalib
{

/**
 * @brief Inherit this class to create a self-destroying class.
 *
 * Allows for objects to be shared without worrying about who "owns"
 * the object. When a new owner is given the object, addRef() should
 * be called. When finished with the object, subRef() should be
 * called. When the last owner calls subRef(), the object is deleted.
*/
class ReferenceCounter
{
public:
    /**
     * @brief Constructor.  The object will initially have 1 reference.
     **/
    ReferenceCounter() : _refs(1) {};

    /**
     * @brief Add an owner of this object.
     *
     * When the owner is finished with the
     * object, call subRef().
     **/
    void addRef() { vespalib::Atomic::postInc(&_refs); };

    /**
     * @brief Remove an owner of this object.
     *
     * If that was the last owner, delete the object.
     **/
    void subRef() {
        unsigned oldVal = vespalib::Atomic::postDec(&_refs);
        if (oldVal == 1) {
            delete this;
        }
    }
    unsigned refCount() const { return _refs; }
protected:
    /**
     * @brief Destructor.  Does sanity check only.
     **/
    virtual ~ReferenceCounter() { assert (_refs == 0); };
private:
    volatile unsigned _refs;
};

} // namespace vespalib

