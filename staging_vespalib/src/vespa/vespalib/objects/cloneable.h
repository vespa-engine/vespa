// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/**
 * @class vespalib::Cloneable
 * @brief Superclass for objects implementing clone() deep copy.
 */

namespace vespalib {

class Cloneable {
public:
    /**
     * @brief Creates a clone of this instance.
     *
     * Note that the caller takes ownership of the returned object. It
     * is not an unique_ptr since that would not support covariant
     * return types. A class T that inherits this interface should
     * define T* clone() const, such that people cloning a T object
     * don't need to cast it to get the correct type.
     */
    virtual Cloneable* clone() const = 0;
    virtual ~Cloneable() = default;
};

} // namespace vespalib

