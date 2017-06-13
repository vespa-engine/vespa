// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2009 Yahoo

#pragma once

#include <algorithm>

namespace vespalib {

/**
 * Simple utility class used to allocate and own an aligned chunk of
 * memory. The owned memory will be allocated with 'malloc' and will
 * be freed using 'free' on the same pointer as was returned from
 * 'malloc'.
 **/
class AlignedMemory
{
private:
    char *_alloc;
    char *_align;

    AlignedMemory(const AlignedMemory &rhs);
    AlignedMemory &operator=(const AlignedMemory &rhs);
public:
    /**
     * Allocate a chunk of memory with the specified size and
     * alignment.  Specifying a zero size will make the object
     * contain a null pointer.
     *
     * @param size amount of memory to allocate
     * @param align wanted memory alignment
     **/
    AlignedMemory(size_t size, size_t align);

    /**
     * Get pointer to the aligned memory chunk owned by this object.
     * @return aligned memory chunk
     **/
    char *get() { return _align; }

    /**
     * Get pointer to the aligned memory chunk owned by this object.
     * @return aligned memory chunk
     **/
    const char *get() const { return _align; }

    /**
     * Swap the memory owned by this object with the memory owned by
     * another object.
     *
     * @param rhs the other object to swap with
     **/
    void swap(AlignedMemory &rhs);

    /**
     * Free the memory owned by this object.
     **/
    ~AlignedMemory();
};

} // namespace vespalib

namespace std {

template <>
inline void swap<vespalib::AlignedMemory>(vespalib::AlignedMemory &a, vespalib::AlignedMemory &b) {
    a.swap(b);
}

} // namespace std

