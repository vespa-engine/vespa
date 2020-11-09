// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
/**
 * \class vespalib::Identifiable
 * \ingroup util
 *
 * \brief Superclass for objects adding some runtime type information.
 *
 * This class is a superclass used by many other classes to add some runtime
 * information.
 *
 * This can be used to verify type to be able to do cheap static casts
 * instead of dynamic casts. It can be used to identify type of object, and
 * it can also be used to generate an object of the given type (If it's not
 * an identifiable abstract type).
 *
 */

#include "identifiable.h"

namespace vespalib {

template <typename T>
Serializer & Identifiable::serialize(const T & v, Serializer & os) {
    uint32_t sz(v.size());
    os.put(sz);
    for(size_t i(0); i < sz; i++) {
        v[i].serialize(os);
    }
    return os;
}

template <typename T>
Deserializer & Identifiable::deserialize(T & v, Deserializer & is) {
    uint32_t sz(0);
    is.get(sz);
    v.resize(sz);
    for(size_t i(0); i < sz; i++) {
        v[i].deserialize(is);
    }
    return is;
}

template <typename T>
class IdentifiablePtr : public CloneablePtr<T>
{
public:
    IdentifiablePtr(const T &t) : CloneablePtr<T>(t.clone()) {}
    IdentifiablePtr(IdentifiablePtr &&) noexcept = default;
    IdentifiablePtr & operator = (IdentifiablePtr &&) noexcept = default;
    IdentifiablePtr(const IdentifiablePtr &) = default;
    IdentifiablePtr & operator = (const IdentifiablePtr &) = default;
    IdentifiablePtr(T * p=nullptr) noexcept : CloneablePtr<T>(p) { }
    IdentifiablePtr(std::unique_ptr<T> &&rhs) noexcept
        : CloneablePtr<T>(std::move(rhs))
    {
    }
    IdentifiablePtr &operator=(std::unique_ptr<T> &&rhs) noexcept
    {
        CloneablePtr<T>::operator=(std::move(rhs));
        return *this;
    }
    int cmp(const IdentifiablePtr<T> &rhs) const {
        const T *a = this->get();
        const T *b = rhs.get();
        if (a == 0) {
            return (b == 0) ? 0 : -1;
        }
        return (b == 0) ? 1 : a->cmp(*b);
    }
    bool operator  < (const IdentifiablePtr<T> &rhs) const { return (cmp(rhs) < 0); }
    bool operator  > (const IdentifiablePtr<T> &rhs) const { return (cmp(rhs) > 0); }
    bool operator == (const IdentifiablePtr<T> &rhs) const { return (cmp(rhs) == 0); }
    bool operator != (const IdentifiablePtr<T> &rhs) const { return (cmp(rhs) != 0); }
    Serializer & serialize(Serializer & os) const {
        if (this->get()) {
            os.put(uint8_t(1)) << *this->get();
        } else {
            os.put(uint8_t(0));
        }
        return os;
    }
    Deserializer & deserialize(Deserializer & is) {
        uint8_t hasObject;
        is.get(hasObject);
        if (hasObject) {
            this->reset(static_cast<T *>(Identifiable::create(is).release()));
        }
        return is;
    }
    friend Serializer & operator << (Serializer & os, const IdentifiablePtr<T> & agg) { return agg.serialize(os); }
    friend Deserializer & operator >> (Deserializer & is, IdentifiablePtr<T> & agg)   { return agg.deserialize(is); }
};

template <typename T>
class IdentifiableSharedPtr : public std::shared_ptr<T>
{
public:
    IdentifiableSharedPtr(const T &t) : std::shared_ptr<T>(t.clone()) {}
    IdentifiableSharedPtr(T * p=nullptr) : std::shared_ptr<T>(p) { }
    int cmp(const IdentifiableSharedPtr<T> &rhs) const {
        const T *a = this->get();
        const T *b = rhs.get();
        if (a == 0) {
            return (b == 0) ? 0 : -1;
        }
        return (b == 0) ? 1 : a->cmp(*b);
    }
    bool operator < (const IdentifiableSharedPtr<T> &rhs) const {
        return (cmp(rhs) < 0);
    }
    Serializer & serialize(Serializer & os) const {
        if (this->get()) {
            os.put(uint8_t(1)) << *this->get();
        } else {
            os.put(uint8_t(0));
        }
        return os;
    }
    Deserializer & deserialize(Deserializer & is) {
        uint8_t hasObject;
        is.get(hasObject);
        if (hasObject) {
            reset(static_cast<T *>(Identifiable::create(is).release()));
        }
        return is;
    }
    friend Serializer & operator << (Serializer & os, const IdentifiableSharedPtr<T> & agg) { return agg.serialize(os); }
    friend Deserializer & operator >> (Deserializer & is, IdentifiableSharedPtr<T> & agg)   { return agg.deserialize(is); }
};

}
