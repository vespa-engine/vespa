// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute {

/*
 * Class describing how to compact a reference attribute
 */
class ReferenceAttributeCompactionSpec {
    bool           _values;
    bool           _dictionary;
public:
    ReferenceAttributeCompactionSpec() noexcept
        : _values(false),
          _dictionary(false)
    {
    }
    ReferenceAttributeCompactionSpec(bool values_, bool dictionary_) noexcept
        : _values(values_),
          _dictionary(dictionary_)
    {
    }
    bool values() const noexcept { return _values; }
    bool dictionary() const noexcept { return _dictionary; }
};

}
