// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/attributevector.h>
#include <boost/noncopyable.hpp>

namespace search {

/**
 * General class for guarding a component that is using an underlying generation handler.
 **/
template <typename T>
class ComponentGuard
{
private:
    typename T::SP           _component;
    typedef vespalib::GenerationHandler::Guard Guard;
    Guard _generationGuard;
public:
    ComponentGuard();
    virtual ~ComponentGuard() { }
    /**
     * Creates a guard for the shared pointer of the given component.
     **/
    ComponentGuard(const typename T::SP & component);
    const T & get()          const { return *_component; }

    const typename T::SP & getSP(void) const { return _component; }
    const T * operator -> () const { return _component.get(); }
    const T & operator * ()  const { return *_component.get(); }
    T & get()                      { return *_component; }
    T * operator -> ()             { return _component.get(); }
    T & operator * ()              { return *_component.get(); }
    bool valid()             const { return _component.get() != NULL; }
};

template <typename T>
ComponentGuard<T>::ComponentGuard() :
    _component(),
    _generationGuard()
{
}

template <typename T>
ComponentGuard<T>::ComponentGuard(const typename T::SP & component) :
    _component(component),
    _generationGuard(valid() ? _component->takeGenerationGuard() : Guard())
{
}

/**
 * This class makes sure that you will have a consistent view per document in the attribute vector
 * while the guard is held.
 **/
class AttributeGuard : public ComponentGuard<AttributeVector>
{
public:
    typedef std::unique_ptr<AttributeGuard> UP;
    typedef std::shared_ptr<AttributeGuard> SP;
    AttributeGuard();
    AttributeGuard(const AttributeVector::SP & attribute);
};

/**
 * This class makes sure that the attribute vector is not updated with enum changes while the guard is held.
 **/
class AttributeEnumGuard : public AttributeGuard, public boost::noncopyable
{
public:
    explicit AttributeEnumGuard(const AttributeVector::SP & attribute);
    explicit AttributeEnumGuard(const AttributeGuard & attribute);
private:
    mutable std::shared_lock<std::shared_timed_mutex> _lock;
    void takeLock();
};

}

