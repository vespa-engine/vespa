// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "properties.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <cassert>

namespace search::fef {

const Property::Value Property::_emptyValue;
const Property::Values Property::_emptyValues;

const Property::Value &
Property::getAt(uint32_t idx) const noexcept
{
    if (idx < (*_values).size()) {
        return (*_values)[idx];
    }
    return _emptyValue;
}

//-----------------------------------------------------------------------------

uint32_t
Properties::rawHash(const void *buf, uint32_t len) noexcept
{
    uint32_t res = 0;
    unsigned const char *pt = (unsigned const char *) buf;
    unsigned const char *end = pt + len;
    while (pt < end) {
        res = (res << 7) + (res >> 25) + *pt++;
    }
    return res;
}

Properties::Properties() noexcept
    : _numValues(0),
      _data()
{
}

Properties::Properties(const Properties &) = default;
Properties & Properties::operator=(const Properties &) = default;

Properties::~Properties()
{
    assert(_numValues >= _data.size());
}

Properties &
Properties::add(vespalib::stringref key, vespalib::stringref value)
{
    if (!key.empty()) {
        Value & v = _data[key];
        v.push_back(value);
        ++_numValues;
    }
    return *this;
}

uint32_t
Properties::count(vespalib::stringref key) const noexcept
{
    if (!key.empty()) {
        auto node = _data.find(key);
        if (node != _data.end()) {
            return node->second.size();
        }
    }
    return 0;
}

Properties &
Properties::remove(vespalib::stringref key)
{
    if (!key.empty()) {
        auto node = _data.find(key);
        if (node != _data.end()) {
            _numValues -= node->second.size();
            _data.erase(node);
        }
    }
    return *this;
}

Properties &
Properties::import(const Properties &src)
{
    for (const auto& elem : src._data) {
        Map::insert_result res = _data.insert(Map::value_type(elem.first, elem.second));
        if ( ! res.second) {
            _numValues -= res.first->second.size();
            res.first->second = elem.second;
        }
        _numValues += elem.second.size();
    }
    return *this;
}

Properties &
Properties::clear()
{
    if (_data.empty()) {
        return *this;
    }
    {
        Map empty;
        std::swap(_data, empty);
    }
    _numValues = 0;
    return *this;
}

bool
Properties::operator==(const Properties &rhs) const noexcept
{
    return (_numValues == rhs._numValues &&
            _data == rhs._data);
}

uint32_t
Properties::hashCode() const noexcept
{
    uint32_t hash = numKeys() + numValues();
    for (const auto& elem : _data) {
        const Key &key = elem.first;
        const Value &value = elem.second;
        hash += rawHash(key.data(), key.size());
        for (const auto& velem : value) {
            hash += rawHash(velem.data(), velem.size());
        }
    }
    return hash;
}

void
Properties::visitProperties(IPropertiesVisitor &visitor) const
{
    for (const auto& elem : _data) {
        visitor.visitProperty(elem.first, Property(elem.second));
    }
}

void
Properties::visitNamespace(vespalib::stringref ns,
                           IPropertiesVisitor &visitor) const
{
    vespalib::string tmp;
    vespalib::string prefix = ns + ".";
    for (const auto& elem : _data) {
        if ((elem.first.find(prefix) == 0) &&
            (elem.first.size() > prefix.size()))
        {
            tmp = vespalib::stringref(elem.first.data() + prefix.size(),
                                      elem.first.size() - prefix.size());
            visitor.visitProperty(tmp, Property(elem.second));
        }
    }
}

Property
Properties::lookup(vespalib::stringref key) const noexcept
{
    if (key.empty()) {
        return Property();
    }
    auto node = _data.find(key);
    if (node == _data.end()) {
        return Property();
    }
    return Property(node->second);
}

Property Properties::lookup(vespalib::stringref namespace1,
                            vespalib::stringref key) const noexcept
{
    if (namespace1.empty() || key.empty()) {
        return Property();
    }
    vespalib::string fullKey(namespace1);
    fullKey.append('.').append(key);
    return lookup(fullKey);
}

Property Properties::lookup(vespalib::stringref namespace1,
                            vespalib::stringref namespace2,
                            vespalib::stringref key) const noexcept
{
    if (namespace1.empty() || namespace2.empty() || key.empty()) {
        return Property();
    }
    vespalib::string fullKey(namespace1);
    fullKey.append('.').append(namespace2).append('.').append(key);
    return lookup(fullKey);
}

Property Properties::lookup(vespalib::stringref namespace1,
                            vespalib::stringref namespace2,
                            vespalib::stringref namespace3,
                            vespalib::stringref key) const noexcept
{
    if (namespace1.empty() || namespace2.empty() || namespace3.empty() || key.empty()) {
        return Property();
    }
    vespalib::string fullKey(namespace1);
    fullKey.append('.').append(namespace2).append('.').append(namespace3).append('.').append(key);
    return lookup(fullKey);
}

void Properties::swap(Properties & rhs) noexcept
{
    _data.swap(rhs._data);
    std::swap(_numValues, rhs._numValues);
}

}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, search::fef::Property::Values);
