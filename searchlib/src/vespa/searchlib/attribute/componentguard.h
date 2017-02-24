// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/generationhandler.h>
#include <memory>

namespace search {

/**
 * General class for guarding a component that is using an underlying generation handler.
 **/
template <typename T>
class ComponentGuard
{
private:
    using Guard = vespalib::GenerationHandler::Guard;
    using Component = std::shared_ptr<T>;
    Component  _component;
    Guard      _generationGuard;
public:
    ComponentGuard();
    ComponentGuard(ComponentGuard &&) = default;
    ComponentGuard & operator = (ComponentGuard &&) = default;
    ComponentGuard(const ComponentGuard &);
    ComponentGuard & operator = (const ComponentGuard &);
    virtual ~ComponentGuard();
    /**
     * Creates a guard for the shared pointer of the given component.
     **/
    ComponentGuard(const Component & component);
    const T * get()          const { return _component.get(); }

    const Component & getSP(void) const { return _component; }
    const T * operator -> () const { return _component.get(); }
    const T & operator * ()  const { return *_component.get(); }
    T * get()                      { return _component.get(); }
    T * operator -> ()             { return _component.get(); }
    T & operator * ()              { return *_component.get(); }
    bool valid()             const { return _component.get() != NULL; }
};

}
