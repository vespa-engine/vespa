// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "field.h"
#include <vespa/vespalib/objects/cloneable.h>
#include <vespa/document/util/identifiableid.h>
#include <vector>

namespace vespalib { class ObjectVisitor; }

namespace document {

class FieldValue;
class DataType;
class MapDataType;
class WeightedSetDataType;
class ArrayDataType;

class FieldPathEntry {
public:
    enum Type {
        STRUCT_FIELD,
        ARRAY_INDEX,
        MAP_KEY,
        MAP_ALL_KEYS,
        MAP_ALL_VALUES,
        VARIABLE,
        NONE
    };
    using FieldValueCP = vespalib::CloneablePtr<FieldValue>;

    /**
       Creates a empty field path entry.
    */
    FieldPathEntry();

    FieldPathEntry(FieldPathEntry &&) noexcept = default;
    FieldPathEntry & operator=(FieldPathEntry &&) noexcept = default;
    FieldPathEntry(const FieldPathEntry &);
    FieldPathEntry & operator=(const FieldPathEntry &);

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
    FieldPathEntry(const DataType & dataType, const DataType& fillType, std::unique_ptr<FieldValue> lookupKey);

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

    FieldPathEntry * clone() const { return new FieldPathEntry(*this); }

    Type getType() const { return _type; }
    const vespalib::string & getName() const { return _name; }

    const DataType& getDataType() const;

    bool hasField() const { return _field.valid(); }
    const Field & getFieldRef() const { return _field; }

    uint32_t getIndex() const { return _lookupIndex; }

    const FieldValueCP & getLookupKey() const { return _lookupKey; }

    const vespalib::string& getVariableName() const { return _variableName; }

    FieldValue * getFieldValueToSetPtr() const { return _fillInVal.get(); }
    FieldValue & getFieldValueToSet() const { return *_fillInVal; }
    std::unique_ptr<FieldValue> stealFieldValueToSet() const;
    void visitMembers(vespalib::ObjectVisitor &visitor) const;
    /**
     * Parses a string of the format {["]escaped string["]} to its unescaped value.
     * @param key is the incoming value, and contains what is left when done.
     * *return The unescaped value
     */
    static vespalib::string parseKey(vespalib::stringref & key);
private:
    void setFillValue(const DataType & dataType);
    Type                 _type;
    vespalib::string     _name;
    Field                _field;
    const DataType     * _dataType;
    uint32_t             _lookupIndex;
    FieldValueCP         _lookupKey;
    vespalib::string     _variableName;
    mutable FieldValueCP _fillInVal;
};

//typedef std::deque<FieldPathEntry> FieldPath;
// Facade over FieldPathEntry container that exposes cloneability
class FieldPath {
    typedef std::vector<vespalib::CloneablePtr<FieldPathEntry>> Container;
public:
    typedef Container::reference reference;
    typedef Container::const_reference const_reference;
    typedef Container::iterator iterator;
    typedef Container::const_iterator const_iterator;
    typedef Container::reverse_iterator reverse_iterator;
    typedef Container::const_reverse_iterator const_reverse_iterator;
    typedef std::unique_ptr<FieldPath> UP;

    FieldPath();
    FieldPath(const FieldPath &);
    FieldPath & operator=(const FieldPath &);
    FieldPath(FieldPath &&) = default;
    FieldPath & operator=(FieldPath &&) = default;
    ~FieldPath();

    template <typename InputIterator>
    FieldPath(InputIterator first, InputIterator last)
        : _path(first, last)
    { }

    iterator insert(iterator pos, std::unique_ptr<FieldPathEntry> entry);
    void push_back(std::unique_ptr<FieldPathEntry> entry);

    iterator begin() { return _path.begin(); }
    iterator end() { return _path.end(); }
    const_iterator begin() const { return _path.begin(); }
    const_iterator end() const { return _path.end(); }
    reverse_iterator rbegin() { return _path.rbegin(); }
    reverse_iterator rend() { return _path.rend(); }
    const_reverse_iterator rbegin() const { return _path.rbegin(); }
    const_reverse_iterator rend() const { return _path.rend(); }

    FieldPathEntry & front() { return *_path.front(); }
    const FieldPathEntry & front() const { return *_path.front(); }
    FieldPathEntry & back() { return *_path.back(); }
    const FieldPathEntry & back() const { return *_path.back(); }

    void pop_back();
    void clear();
    void reserve(size_t sz);

    Container::size_type size() const { return _path.size(); }
    bool empty() const { return _path.empty(); }
    FieldPathEntry & operator[](Container::size_type i) { return *_path[i]; }

    const FieldPathEntry & operator[](Container::size_type i) const { return *_path[i]; }

    void visitMembers(vespalib::ObjectVisitor &visitor) const;

    template <typename IT>
    class Range {
    public:
        Range() : _begin(), _end() { }
        Range(IT begin_, IT end_) : _begin(begin_), _end(end_) { }
        Range next() const { return Range(_begin+1, _end); }
        bool atEnd() const { return _begin == _end; }
        const FieldPathEntry & cur() { return **_begin; }
    private:
        IT _begin;
        IT _end;
    };

    Range<const_iterator> getFullRange() const { return Range<const_iterator>(begin(), end()); }
private:
    Container _path;
};

}
