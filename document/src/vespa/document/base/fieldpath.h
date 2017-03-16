// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/cloneable.h>
#include <vespa/document/util/identifiableid.h>
#include <memory>
#include <vector>

namespace vespalib {
class ObjectVisitor;
}

namespace document {

class FieldValue;
class DataType;
class MapDataType;
class WeightedSetDataType;
class ArrayDataType;
class Field;

class FieldPathEntry : public vespalib::Identifiable {
public:
    DECLARE_IDENTIFIABLE_NS(document, FieldPathEntry);
    enum Type {
        STRUCT_FIELD,
        ARRAY_INDEX,
        MAP_KEY,
        MAP_ALL_KEYS,
        MAP_ALL_VALUES,
        VARIABLE,
        NONE
    };
    typedef std::shared_ptr<const Field> FieldSP;
    typedef vespalib::CloneablePtr<DataType> DataTypeCP;
    typedef vespalib::CloneablePtr<FieldValue> FieldValueCP;

    /**
       Creates a empty field path entry.
    */
    FieldPathEntry();

    /**
       Creates a field path entry for a struct field lookup.
    */
    FieldPathEntry(const Field &fieldRef);

    /**
       Creates a field path entry for an array lookup.
    */
    FieldPathEntry(const DataType & dataType, uint32_t index);

    /**
       Creates a field path entry for a map or wset key lookup.
    */
    FieldPathEntry(const DataType & dataType, const DataType& fillType,
                   const FieldValueCP & lookupKey);

    /**
       Creates a field path entry for a map key or value only traversal.
    */
    FieldPathEntry(const DataType & dataType, const DataType& keyType,
                   const DataType& valueType, bool keysOnly, bool valuesOnly);

    ~FieldPathEntry();
    /**
       Creates a field entry for an array, map or wset traversal using a variable.
    */
    FieldPathEntry(const DataType & dataType, const vespalib::stringref & variableName);

    Type getType() const { return _type; }
    const vespalib::string & getName() const { return _name; }

    const DataType& getDataType() const;

    bool hasField() const { return _fieldRef.get(); }
    const Field & getFieldRef() const { return *_fieldRef; }

    uint32_t getIndex() const { return _lookupIndex; }

    const FieldValueCP & getLookupKey() const { return _lookupKey; }

    const vespalib::string& getVariableName() const { return _variableName; }

    FieldValue * getFieldValueToSetPtr() const { return _fillInVal.get(); }
    FieldValue& getFieldValueToSet() const { return *_fillInVal; }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    /**
     * Parses a string of the format {["]escaped string["]} to its unescaped value.
     * @param key is the incoming value, and contains what is left when done.
     * *return The unescaped value
     */
    static vespalib::string parseKey(vespalib::string & key);
private:
    void setFillValue(const DataType & dataType);
    Type              _type;
    vespalib::string  _name;
    FieldSP           _fieldRef;
    const DataType *  _dataType;
    uint32_t          _lookupIndex;
    FieldValueCP      _lookupKey;
    vespalib::string  _variableName;
    mutable FieldValueCP _fillInVal;
};

//typedef std::deque<FieldPathEntry> FieldPath;
// Facade over FieldPathEntry container that exposes cloneability
class FieldPath : public vespalib::Cloneable {
    typedef std::vector<FieldPathEntry> Container;
public:
    typedef Container::reference reference;
    typedef Container::const_reference const_reference;
    typedef Container::iterator iterator;
    typedef Container::const_iterator const_iterator;
    typedef Container::reverse_iterator reverse_iterator;
    typedef Container::const_reverse_iterator const_reverse_iterator;
    typedef std::unique_ptr<FieldPath> UP;

    FieldPath();
    FieldPath(const FieldPath& other);
    FieldPath& operator=(const FieldPath& rhs);
    ~FieldPath();

    template <typename InputIterator>
    FieldPath(InputIterator first, InputIterator last)
        : _path(first, last)
    { }

    iterator insert(iterator pos, const FieldPathEntry& entry);
    void push_back(const FieldPathEntry& entry);

    iterator begin() { return _path.begin(); }
    iterator end() { return _path.end(); }
    const_iterator begin() const { return _path.begin(); }
    const_iterator end() const { return _path.end(); }
    reverse_iterator rbegin() { return _path.rbegin(); }
    reverse_iterator rend() { return _path.rend(); }
    const_reverse_iterator rbegin() const { return _path.rbegin(); }
    const_reverse_iterator rend() const { return _path.rend(); }

    reference front() { return _path.front(); }
    const_reference front() const { return _path.front(); }
    reference back() { return _path.back(); }
    const_reference back() const { return _path.back(); }

    void pop_back();
    void clear();

    Container::size_type size() const { return _path.size(); }
    bool empty() const { return _path.empty(); }
    reference operator[](Container::size_type i) {
        return _path[i];
    }

    const_reference operator[](Container::size_type i) const {
        return _path[i];
    }

    FieldPath* clone() const {
        return new FieldPath(*this);
    }

    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;

private:
    Container _path;
};

}

