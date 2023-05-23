// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/handle.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/small_vector.h>
#include <vector>

namespace search::fef {
    class MatchData;
    class TermFieldMatchData;
}
namespace search::queryeval {

/**
 * Base description of a single field to be searched.
 **/
class FieldSpecBase
{
public:
    FieldSpecBase(uint32_t fieldId, fef::TermFieldHandle handle, bool isFilter_ = false);

    // resolve where to put match information for this term/field combination
    fef::TermFieldMatchData *resolve(fef::MatchData &md) const;
    const fef::TermFieldMatchData *resolve(const fef::MatchData &md) const;
    uint32_t getFieldId() const { return _fieldId & 0xffffff; }
    fef::TermFieldHandle getHandle() const { return _handle; }
    /// a filter produces less detailed match data
    bool isFilter() const { return _fieldId & 0x1000000; }
private:
    uint32_t             _fieldId;  // field id in ranking framework
    fef::TermFieldHandle _handle;   // handle used when exposing match data to ranking framework
};

/**
 * Description of a single field to be searched.
 **/
class FieldSpec : public FieldSpecBase
{
public:
    FieldSpec(const vespalib::string & name, uint32_t fieldId,
              fef::TermFieldHandle handle, bool isFilter_ = false)
        : FieldSpecBase(fieldId, handle, isFilter_),
          _name(name)
    {}
    ~FieldSpec();

    const vespalib::string & getName() const { return _name; }
private:
    vespalib::string     _name;     // field name
};

/**
 * List of fields to be searched.
 **/
class FieldSpecBaseList
{
private:
    using List = vespalib::SmallVector<FieldSpecBase, 1>;
    List _list;

public:
    FieldSpecBaseList() = default;
    FieldSpecBaseList(FieldSpecBaseList &&) noexcept = default;
    FieldSpecBaseList & operator=(FieldSpecBaseList &&) noexcept = default;
    FieldSpecBaseList(const FieldSpecBaseList &) = default;
    FieldSpecBaseList & operator=(FieldSpecBaseList &) = delete;
    ~FieldSpecBaseList();
    void reserve(size_t sz) { _list.reserve(sz); }
    using const_iterator = const FieldSpecBase *;
    FieldSpecBaseList &add(const FieldSpecBase &spec) {
        _list.push_back(spec);
        return *this;
    }
    bool empty() const { return _list.empty(); }
    size_t size() const { return _list.size(); }
    const_iterator begin() const { return _list.begin(); }
    const_iterator end() const { return _list.end(); }
    const FieldSpecBase &operator[](size_t i) const { return _list[i]; }
};

/**
 * List of fields to be searched.
 **/
class FieldSpecList
{
private:
    vespalib::SmallVector<FieldSpec, 1> _list;

public:
    FieldSpecList() = default;
    FieldSpecList(FieldSpecList &&) noexcept = delete;
    FieldSpecList & operator=(FieldSpecList &&) noexcept = delete;
    FieldSpecList(const FieldSpecList &) noexcept = delete;
    FieldSpecList & operator=(const FieldSpecList &) noexcept = delete;
    ~FieldSpecList();
    FieldSpecList &add(const FieldSpec &spec) {
        _list.push_back(spec);
        return *this;
    }
    bool empty() const { return _list.empty(); }
    size_t size() const { return _list.size(); }
    const FieldSpec &operator[](size_t i) const { return _list[i]; }
    void clear() { _list.clear(); }
};

}
