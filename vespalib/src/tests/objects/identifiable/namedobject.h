// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/identifiable.h>
#include <string>

namespace vespalib
{

class NamedObject : public Identifiable
{
public:
    DECLARE_IDENTIFIABLE_NS(vespalib, NamedObject);
    DECLARE_NBO_SERIALIZE;
    NamedObject() : _name() { }
    NamedObject(const string & name) : _name(name) { }
    const string & getName() const { return _name; }
private:
    string _name;
};

}

