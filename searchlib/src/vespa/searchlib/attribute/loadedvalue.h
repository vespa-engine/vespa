// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/datastore/entryref.h>

namespace search
{

namespace attribute
{

/*
 * Temporary representation of enumerated attribute loaded from non-enumerated
 * save file (i.e. old save format).
 */
template <typename T>
class LoadedValue
{
public:
    LoadedValue()
        : _docId(0),
          _idx(0),
          _pidx(0),
          _weight(1)
    {
        memset(&_value, 0, sizeof(_value));
    }

    class DocRadix
    {
    public:
        uint64_t
        operator() (const LoadedValue<T> & v) const
        {
            uint64_t tmp(v._docId);
            return tmp << 32 | v._idx;
        }
    };

    class DocOrderCompare
    {
    public:
        bool
        operator()(const LoadedValue<T> &x,
                   const LoadedValue<T> &y) const
        {
            int32_t diff(x._docId - y._docId);
            if (diff == 0) {
                diff = x._idx - y._idx;
            }
            return diff < 0;
        }
    };

    IEnumStore::Index
    getEidx() const
    {
        return IEnumStore::Index(vespalib::datastore::EntryRef(_value._eidx));
    }

    void
    setEidx(IEnumStore::Index v)
    {
        _value._eidx = v.ref();
    }

    T
    getValue() const
    {
        return _value._value;
    }

    inline void
    setValue(T v)
    {
        _value._value = v;
    }

    int32_t
    getWeight() const
    {
        return _weight;
    }

    void
    setWeight(int32_t v)
    {
        _weight = v;
    }

    inline bool
    operator<(const LoadedValue<T> &rhs) const
    {
        return _value._value < rhs._value._value;
    }
    
    union Value {
        T        _value;
        uint32_t _eidx;
    };

    uint32_t                         _docId;
    uint32_t                         _idx;
    vespalib::datastore::EntryRef    _pidx;
private:
    int32_t                          _weight;
    Value                            _value;
};


template <>
inline void
LoadedValue<float>::setValue(float v)
{
    // Consolidate nans during load to avoid sort order issues
    _value._value = isUndefined<float>(v) ? getUndefined<float>() : v;
}

template <>
inline void
LoadedValue<double>::setValue(double v)
{
    // Consolidate nans during load to avoid sort order issues
    _value._value = isUndefined<double>(v) ? getUndefined<double>() : v;
}


template <>
inline bool
LoadedValue<float>::operator<(const LoadedValue<float> &rhs) const
{
    if (std::isnan(_value._value)) {
        return !std::isnan(rhs._value._value);
    }
    if (std::isnan(rhs._value._value)) {
        return false;
    }
    return _value._value < rhs._value._value;
}


template <>
inline bool
LoadedValue<double>::operator<(const LoadedValue<double> &rhs) const
{
    if (std::isnan(_value._value)) {
        return !std::isnan(rhs._value._value);
    }
    if (std::isnan(rhs._value._value)) {
        return false;
    }
    return _value._value < rhs._value._value;
}


} // namespace attribute

} // namespace search

