// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_map.h>

namespace search::fef {

class Anything
{
public:
   typedef std::unique_ptr<Anything> UP;
   virtual ~Anything() { }
};

class IObjectStore
{
public:
    virtual ~IObjectStore() { }
    virtual void add(const vespalib::string & key, Anything::UP value) = 0;
    virtual const Anything * get(const vespalib::string & key) const = 0;
};

class ObjectStore : public IObjectStore
{
public:
    ObjectStore();
    ~ObjectStore();
    void add(const vespalib::string & key, Anything::UP value) override;
    const Anything * get(const vespalib::string & key) const override;
private:
    typedef vespalib::hash_map<vespalib::string, Anything *> ObjectMap;
    ObjectMap _objectMap;
};

}
