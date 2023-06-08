// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_map.h>

namespace search::fef {

/**
 * Top level interface for things to store in an IObjectStore.
 */
class Anything
{
public:
   using UP = std::unique_ptr<Anything>;
   virtual ~Anything() { }
};

/**
 * Implementation of the Anything interface that wraps a value of the given type.
 */
template<typename T>
class AnyWrapper : public Anything
{
public:
    AnyWrapper(T value) : _value(std::move(value)) { }
    const T & getValue() const { return _value; }
    static const T & getValue(const Anything & any) { return static_cast<const AnyWrapper &>(any).getValue(); }
private:
    T _value;
};

/**
 * Interface for a key value store of Anything instances.
 */
class IObjectStore
{
public:
    virtual ~IObjectStore() { }
    virtual void add(const vespalib::string & key, Anything::UP value) = 0;
    virtual const Anything * get(const vespalib::string & key) const = 0;
};

/**
 * Object store implementation on top of a hash map.
 */
class ObjectStore : public IObjectStore
{
public:
    ObjectStore();
    ~ObjectStore();
    void add(const vespalib::string & key, Anything::UP value) override;
    const Anything * get(const vespalib::string & key) const override;
private:
    using ObjectMap = vespalib::hash_map<vespalib::string, Anything *>;
    ObjectMap _objectMap;
};

namespace objectstore {

/**
 * Utility function that gets the value stored in an Anything instance (via AnyWrapper).
 */
template<typename T>
const T &
as_value(const Anything &val) {
    using WrapperType = AnyWrapper<T>;
    const auto *wrapper = dynamic_cast<const WrapperType *>(&val);
    return wrapper->getValue();
}

}

}
