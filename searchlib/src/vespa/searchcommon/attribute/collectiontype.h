// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::attribute {

class CollectionType
{
 public:
    enum Type {
        /**
         * Single value type with one value stored for each document.
         **/
        SINGLE = 0,
        /**
         * Array type with zero to n values stored for each document.
         **/
        ARRAY = 1,
        /**
         * Weighted set type with zero to n unique values stored for each document.
         * In addition each unique value is accociated with a weight.
         **/
        WSET = 2,
        MAX_TYPE
    };

    CollectionType(Type t = SINGLE, bool remove = false, bool create = false) :
        _type(t),
        _removeIfZero(remove),
        _createIfNonExistant(create)
    {
    }

    explicit
    CollectionType(const vespalib::string & t, bool remove = false, bool create = false) :
        _type(asType(t)),
        _removeIfZero(remove),
        _createIfNonExistant(create)
    {
    }

    Type type()                const { return _type; }
    bool isMultiValue()        const { return _type != SINGLE; }
    bool isWeightedSet()       const { return _type == WSET; }
    bool isArray()             const { return _type == ARRAY; }
    bool removeIfZero()        const { return _removeIfZero; }
    bool createIfNonExistant() const { return _createIfNonExistant; }
    const char * asString()    const { return asString(_type); }
    void removeIfZero(bool newValue) { _removeIfZero = newValue; }
    void createIfNonExistant(bool newValue) { _createIfNonExistant = newValue; }
    bool operator!=(const CollectionType &b) const { return !(operator==(b)); }
    bool operator==(const CollectionType &b) const {
        return _type == b._type &&
               _removeIfZero == b._removeIfZero &&
               _createIfNonExistant == b._createIfNonExistant;
    }

  private:
    struct TypeInfo {
        Type _type;
        const char * _name;
    };

    static const char * asString(Type t) { return _typeTable[t]._name; }
    static Type asType(const vespalib::string &t);

    Type _type;
    bool _removeIfZero;
    bool _createIfNonExistant;
    static const TypeInfo _typeTable[MAX_TYPE];
};

}
