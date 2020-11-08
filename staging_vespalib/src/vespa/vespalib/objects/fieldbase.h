// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

class IFieldBase
{
public:
    virtual ~IFieldBase() = default;
    // Overrides must guarantee that returned reference is zero-terminated.
    virtual stringref getName() const = 0;
};

class FieldBase : public IFieldBase
{
public:
    FieldBase(stringref name) : _name(name) { }
    stringref getName() const final override { return _name; }
private:
    string _name;
};

class SerializerCommon
{
protected:
    static FieldBase _unspecifiedField;
    static FieldBase _sizeField;
};

}

