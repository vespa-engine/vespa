// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <typeindex>
#include <array>
#include <cstddef>

#pragma once

namespace vespalib::eval::instruction {

/*
 * Utilities for detecting implementation class by comparing
 * typeindex(typeid(T)); for now these are local to this
 * namespace, but we can consider moving them to a more
 * common place (probably vespalib) if we see more use-cases.
 */

/**
 * Recognize a (const) instance of type T.  This is cheaper than
 * dynamic_cast, but requires the object to be exactly of class T.
 * Returns a pointer to the object as T if recognized, nullptr
 * otherwise.
 **/
template<typename T, typename U>
const T *
recognize_by_type_index(const U & object)
{
    if (std::type_index(typeid(object)) == std::type_index(typeid(T))) {
        return static_cast<const T *>(&object);
    }
    return nullptr;
}

/**
 * Packs N recognized values into one object, used as return value
 * from detect_type<T>.
 * 
 * Use all_converted() or the equivalent bool cast operator to check
 * if all objects were recognized.  After this check is successful use
 * get<0>(), get<1>() etc to get a reference to the objects.
 **/
template<typename T, size_t N>
class RecognizedValues
{
private:
    std::array<const T *, N> _pointers;
public:
    RecognizedValues(std::array<const T *, N> && pointers)
        : _pointers(std::move(pointers))
    {}
    bool all_converted() const {
        for (auto p : _pointers) {
            if (p == nullptr) return false;            
        }
        return true;
    }
    operator bool() const { return all_converted(); }
    template<size_t idx> const T& get() const {
        static_assert(idx < N);
        return *_pointers[idx];
    }
};

/**
 * For all arguments, detect if they have typeid(T), convert to T if
 * possible, and return a RecognizedValues packing the converted
 * values.
 **/
template<typename T, typename... Args>
RecognizedValues<T, sizeof...(Args)>
detect_type(const Args &... args)
{
    return RecognizedValues<T, sizeof...(Args)>({(recognize_by_type_index<T>(args))...});
}

} // namespace
