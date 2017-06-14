// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2004 Overture Services Norway AS

#pragma once

#include <algorithm>
#include <memory>
#include <assert.h>

namespace vespalib {

/**
 * @brief A LinkedPtr is a smart pointer implementing reference
 * linking.
 *
 * Multiple instances share the ownership of an object by being linked
 * together. This has the advantage of not needing external
 * book-keeping. However, note that LinkedPtr may only be used to
 * share objects within a thread, as there is no internal
 * synchronization.
 *
 * If you need to share an object between threads, take a look at
 * std::shared_ptr.
 **/
template <typename T>
class LinkedPtr
{
private:

    mutable const LinkedPtr *_prev;
    mutable const LinkedPtr *_next;
    T                       *_obj;

    /**
     * Unlink this pointer
     **/
    void unlink() {
        if (_prev == this) {
            delete _obj;
        } else {
            _prev->_next = _next;
            _next->_prev = _prev;
        }
    }

    /**
     * Link this pointer
     **/
    void link(const LinkedPtr &rhs) {
        if (rhs._obj != 0) {
            _obj = rhs._obj;
            _prev = &rhs;
            _next = rhs._next;
            rhs._next = this;
            _next->_prev = this;
        } else {
            _obj = 0;
            _next = this;
            _prev = this;
        }
    }

public:
    /**
     * @brief Create a LinkedPtr owning the given object
     *
     * @param obj the object, may be 0
     **/
    explicit LinkedPtr(T *obj = 0)
        : _prev(this), _next(this), _obj(obj) {}

    /**
     * @brief Copy constructor
     *
     * Copying a LinkedPtr will result in a new LinkedPtr sharing the
     * ownership of the object held by the original LinkedPtr.
     *
     * @param rhs copy this
     **/
    LinkedPtr(const LinkedPtr &rhs)
        : _prev(this), _next(this), _obj(0)
    {
        link(rhs);
    }

    /**
     * @brief Delete the pointed to object if we are the last
     * LinkedPtr sharing ownership of it
     **/
    ~LinkedPtr() {
        unlink();
    }

    /**
     * @brief Assignment operator
     *
     * @return reference to this
     * @param rhs copy this
     **/
    LinkedPtr &operator= (const LinkedPtr &rhs) {
        if (_obj == rhs._obj) {
            return *this;
        }
        unlink();
        link(rhs);
        return *this;
    }

    /**
     * @brief Check if this LinkedPtr points to anything
     *
     * @return true if we point to something
     **/
    bool isSet() const { return (_obj != 0); }

    /**
     * @brief Obtain the object being pointed to
     *
     * @return the object (by pointer)
     **/
    T *get() const { return _obj; }

    bool operator == (const LinkedPtr & rhs) const { return (_obj == rhs._obj) ||
                                                            ( (_obj != NULL) &&
                                                              (rhs._obj != NULL) &&
                                                              (*_obj == *rhs._obj)); }

    /**
     * @brief Access the object being pointed to
     *
     * This is the preferred way to access the object being pointed to
     * as it makes the LinkedPtr look like a naked pointer.
     *
     * @return the object (by pointer)
     **/
    T *operator->() const { return get(); }

    /**
     * @brief Obtain the object being pointed to
     *
     * @return the object (by reference)
     **/
    T &operator*() const { return *get(); }

    /**
     * @brief Change this pointer
     *
     * This method makes this LinkedPtr drop its current pointer and
     * point to something new. If we are the last owner of the old
     * object, it is deleted. The new object will be owned by this
     * LinkedPtr (just like when using the constructor).
     *
     * @param obj the object, may be 0
     **/
    void reset(T *obj = 0) {
        unlink();
        _obj = obj;
        _next = this;
        _prev = this;
    }

    /**
     * @brief release the object pointed to
     *
     * This is an operation that can only be done when this is the only item
     * in the list.
     *
     * @return the pointer to the owned object or NULL if it is not the only
     * owner.
     **/
    T * release() {
        T * obj(NULL);
        if ((_next == _prev) && (_prev == this)) {
            obj = _obj;
            _obj = NULL;
        }
        return obj;
    }
};

} // namespace vespalib
