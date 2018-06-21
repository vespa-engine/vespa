// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "judymultimap.h"
#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/array.hpp>
#include <set>
#include <ostream>

namespace storage {

template<class T0, class T1, class T2, class T3>
JudyMultiMap<T0, T1, T2, T3>::JudyMultiMap()
    : _values0(1), _values1(1), _values2(1), _values3(1), _free(4)
{ }

template<class T0, class T1, class T2, class T3>
JudyMultiMap<T0, T1, T2, T3>::~JudyMultiMap() { }

template<class T0, class T1, class T2, class T3>
bool
JudyMultiMap<T0, T1, T2, T3>::
operator==(const JudyMultiMap<T0, T1, T2, T3>& map) const
{
    if (size() != map.size()) return false;
    for (typename JudyMultiMap<T0, T1, T2, T3>::const_iterator
            it1 = begin(), it2 = map.begin(); it1 != end(); ++it1, ++it2)
    {
        assert(it2 != end());
        if (*it1 != *it2) return false;
    }
    return true;
}

template<class T0, class T1, class T2, class T3>
bool
JudyMultiMap<T0, T1, T2, T3>::
operator<(const JudyMultiMap<T0, T1, T2, T3>& map) const
{
    if (size() != map.size()) return (size() < map.size());
    for (typename JudyMultiMap<T0, T1, T2, T3>::const_iterator
            it1 = begin(), it2 = map.begin(); it1 != end(); ++it1, ++it2)
    {
        if (it1.key() != it2.key()) return (it1.key() < it2.key());
        if (it1.value() != it2.value()) return (it1.value() < it2.value());
    }
    return false;
}

template<class T0, class T1, class T2, class T3>
typename JudyMultiMap<T0, T1, T2, T3>::size_type
JudyMultiMap<T0, T1, T2, T3>::size() const
{
        // First elements in all vectors is bogus, because we use value 0
        // to mean unset in judyarray. (To be able to detect if we overwrite)
    return _values0.size() + _values1.size()
         + _values2.size() + _values3.size() - 4
         - _free[0].size() - _free[1].size()
         - _free[2].size() - _free[3].size();
}

template<class T0, class T1, class T2, class T3>
void
JudyMultiMap<T0, T1, T2, T3>::
swap(JudyMultiMap<T0, T1, T2, T3>& other)
{
    _judyArray.swap(other._judyArray);
    _values0.swap(other._values0);
    _values1.swap(other._values1);
    _values2.swap(other._values2);
    _values3.swap(other._values3);
    _free.swap(other._free);
}

template<class T0, class T1, class T2, class T3>
typename JudyMultiMap<T0, T1, T2, T3>::const_iterator
JudyMultiMap<T0, T1, T2, T3>::find(key_type key) const
{
    ConstIterator iter(*this, key);
    if (!iter.end() && iter.key() != key) {
        iter = ConstIterator(*this);
    }
    return iter;
}

template<class T0, class T1, class T2, class T3>
typename JudyMultiMap<T0, T1, T2, T3>::iterator
JudyMultiMap<T0, T1, T2, T3>::find(key_type key, bool insertIfNonExisting,
                                   bool& preExisted)
{
    Iterator iter(*this, key);
    if (insertIfNonExisting && (iter.end() || iter.key() != key)) {
        insert(key, T3(), preExisted);
        iter = Iterator(*this, key);
        assert(iter.key() == key);
    } else if (iter.key() != key) {
        preExisted = false;
        iter = Iterator(*this);
    } else {
        preExisted = true;
    }
    return iter;
}

template<class T0, class T1, class T2, class T3>
typename JudyMultiMap<T0, T1, T2, T3>::size_type
JudyMultiMap<T0, T1, T2, T3>::erase(key_type key)
{
    JudyArray::iterator it = _judyArray.find(key);
    if (it == _judyArray.end()) return 0;
    _free[getType(it.value())].push_back(getIndex(it.value()));
    _judyArray.erase(key);
    return 1;
}

template<class T0, class T1, class T2, class T3>
void
JudyMultiMap<T0, T1, T2, T3>::clear()
{
    _judyArray.clear();
    _values0.resize(1);
    _values1.resize(1);
    _values2.resize(1);
    _values3.resize(1);
    _free[0].clear();
    _free[1].clear();
    _free[2].clear();
    _free[3].clear();
}

template<class T0, class T1, class T2, class T3>
const typename JudyMultiMap<T0, T1, T2, T3>::mapped_type
JudyMultiMap<T0, T1, T2, T3>::operator[](key_type key)
{
    bool preExisted;
    JudyArray::iterator it = _judyArray.find(key, true, preExisted);
        // If it doesn't already exist, insert
    if (it.value() == 0) {
        if (_free[0].empty()) {
            it.setValue(getValue(0, _values0.size()));
            _values0.push_back(T0());
        } else {
            it.setValue(getValue(0, _free[0].back()));
            _values0[_free[0].back()] = T0();
            _free[0].pop_back();
        }
    }
    switch (getType(it.value())) {
        case 0: return _values0[getIndex(it.value())];
        case 1: return _values1[getIndex(it.value())];
        case 2: return _values2[getIndex(it.value())];
        case 3: return _values3[getIndex(it.value())];
        default: HDR_ABORT("should not be reached");
    }
    return T0(); // Avoid warning of no return
}

template<class T0, class T1, class T2, class T3>
typename JudyMultiMap<T0, T1, T2, T3>::size_type
JudyMultiMap<T0, T1, T2, T3>::getMemoryUsage() const
{
    return _judyArray.getMemoryUsage()
        + sizeof(T0) * _values0.capacity()
        + sizeof(T1) * _values1.capacity()
        + sizeof(T2) * _values2.capacity()
        + sizeof(T3) * _values3.capacity()
        + sizeof(typename Type0Vector::size_type)
            * (_free[0].capacity() + _free[1].capacity() +
               _free[2].capacity() + _free[3].capacity());
}

template<class T0, class T1, class T2, class T3>
void
JudyMultiMap<T0, T1, T2, T3>::
print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "JudyMultiMap(";

    if (verbose) {
        for (const_iterator i = begin(); i != end(); ++i) {
            out << "\n" << indent << "  ";
            i.print(out, verbose, indent + "  ");
        }
    }

    if (_values0.size() > 1) {
        std::set<typename Type0Vector::size_type> free(
                _free[0].begin(), _free[0].end());
        assert(free.size() == _free[0].size());
        out << "\n" << indent << "  Type0 " << (_values0.size()-1)
            << " entries, " << free.size() << " free {";

        if (verbose) {
            for (uint32_t i=1; i<_values0.size(); ++i) {
                out << "\n" << indent << "    ";
                if (free.find(i) != free.end()) { out << "free"; }
                else { out << _values0[i]; }
            }
        }
        out << "\n" << indent << "  }";
    }
    if (_values1.size() > 1) {
        std::set<typename Type0Vector::size_type> free(
                _free[1].begin(), _free[1].end());
        assert(free.size() == _free[1].size());
        out << "\n" << indent << "  Type1 " << (_values1.size()-1)
            << " entries, " << free.size() << " free {";
        if (verbose) {
            for (uint32_t i=1; i<_values1.size(); ++i) {
                out << "\n" << indent << "    ";
                if (free.find(i) != free.end()) { out << "free"; }
                else { out << _values1[i]; }
            }
        }
        out << "\n" << indent << "  }";
    }
    if (_values2.size() > 1) {
        std::set<typename Type0Vector::size_type> free(
                _free[2].begin(), _free[2].end());
        assert(free.size() == _free[2].size());
        out << "\n" << indent << "  Type2 " << (_values2.size()-1)
            << " entries, " << free.size() << " free {";
        if (verbose) {
            for (uint32_t i=1; i<_values2.size(); ++i) {
                out << "\n" << indent << "    ";
                if (free.find(i) != free.end()) { out << "free"; }
                else { out << _values2[i]; }
            }
        }
        out << "\n" << indent << "  }";
    }

    if (_values3.size() > 1) {
        std::set<typename Type0Vector::size_type> free(
                _free[3].begin(), _free[3].end());
        assert(free.size() == _free[3].size());
        out << "\n" << indent << "  Type3 " << (_values3.size()-1)
            << " entries, " << free.size() << " free {";

        if (verbose) {
            for (uint32_t i=1; i<_values3.size(); ++i) {
                out << "\n" << indent << "    ";
                if (free.find(i) != free.end()) { out << "free"; }
                else { out << _values3[i]; }
            }
        }
        out << "\n" << indent << "  }";
    }
    if (!empty()) { out << "\n" << indent; }
    out << ")";
}

template<class T0, class T1, class T2, class T3>
JudyMultiMap<T0, T1, T2, T3>::
ConstIterator::ConstIterator(const JudyMultiMap<T0, T1, T2, T3>& map)
    : _iterator(map._judyArray.end()),
      _parent(const_cast<JudyMultiMap<T0, T1, T2, T3>*>(&map))
{
}

template<class T0, class T1, class T2, class T3>
JudyMultiMap<T0, T1, T2, T3>::
ConstIterator::ConstIterator(const JudyMultiMap<T0, T1, T2, T3>& map,
                             key_type mykey)
    : _iterator(map._judyArray.lower_bound(mykey)),
      _parent(const_cast<JudyMultiMap<T0, T1, T2, T3>*>(&map))
{
}

template<class T0, class T1, class T2, class T3>
bool
JudyMultiMap<T0, T1, T2, T3>::
ConstIterator::operator==(const JudyMultiMap::ConstIterator &cp) const
{
    return (_iterator == cp._iterator);
}

template<class T0, class T1, class T2, class T3>
typename JudyMultiMap<T0, T1, T2, T3>::value_type
JudyMultiMap<T0, T1, T2, T3>::ConstIterator::operator*() const
{
    switch (getType(_iterator.value())) {
        case 0: return value_type(
            _iterator.key(), _parent->_values0[getIndex(_iterator.value())]);
        case 1: return value_type(
            _iterator.key(), _parent->_values1[getIndex(_iterator.value())]);
        case 2: return value_type(
            _iterator.key(), _parent->_values2[getIndex(_iterator.value())]);
        case 3: return value_type(
            _iterator.key(), _parent->_values3[getIndex(_iterator.value())]);
        default:
            HDR_ABORT("should not be reached");
    }
}

template<class T0, class T1, class T2, class T3>
typename JudyMultiMap<T0, T1, T2, T3>::mapped_type
JudyMultiMap<T0, T1, T2, T3>::ConstIterator::value() const
{
    switch (getType(_iterator.value())) {
        case 0: return _parent->_values0[getIndex(_iterator.value())];
        case 1: return _parent->_values1[getIndex(_iterator.value())];
        case 2: return _parent->_values2[getIndex(_iterator.value())];
        case 3: return _parent->_values3[getIndex(_iterator.value())];
        default:
            HDR_ABORT("should not be reached");
    }
}

template<class T0, class T1, class T2, class T3>
void
JudyMultiMap<T0, T1, T2, T3>::
ConstIterator::print(std::ostream& out, bool, const std::string&) const
{
    if (dynamic_cast<const Iterator*>(this) == 0) {
        out << "Const";
    }
    out << "Iterator(Key: " << _iterator.key() << ", Value: " << value() << ")";
}

template<class T0, class T1, class T2, class T3>
JudyMultiMap<T0, T1, T2, T3>::
Iterator::Iterator(JudyMultiMap<T0, T1, T2, T3>& map)
    : ConstIterator(map) {}

template<class T0, class T1, class T2, class T3>
JudyMultiMap<T0, T1, T2, T3>::
Iterator::Iterator(JudyMultiMap<T0, T1, T2, T3>& map, key_type mykey)
    : ConstIterator(map, mykey) {}

#if 0
template<class T0, class T1, class T2, class T3>
void
JudyMultiMap<T0, T1, T2, T3>::Iterator::setValue(const T3& val)
{
    if (this->_iterator.end()) {
        throw vespalib::IllegalArgumentException(
            "Cannot set value of end() iterator", VESPA_STRLOC);
    }
    insert(this->iterator, val);
}

template<class T0, class T1, class T2, class T3>
void
JudyMultiMap<T0, T1, T2, T3>::Iterator::remove()
{
    if (this->_iterator.end()) {
        throw vespalib::IllegalArgumentException(
            "Cannot erase end() iterator", VESPA_STRLOC);
    }
    int type = getType(this->_iterator.value());
    _free[type].push_back(getIndex(this->_iterator.value()));
    this->_iterator.remove();
}

#endif

template<class T0, class T1, class T2, class T3>
void
JudyMultiMap<T0, T1, T2, T3>::insert(JudyArray::iterator& it, const T3& val)
{
        // Find the type we need to save 'val' as
    int type;
    if      (T0::mayContain(val)) { type = 0; }
    else if (T1::mayContain(val)) { type = 1; }
    else if (T2::mayContain(val)) { type = 2; }
    else { type = 3; }
        // If already pointing to some value, free that resource.
    int oldtype = getType(it.value());
    int index = getIndex(it.value());
    if (index != 0) {
        _free[oldtype].push_back(index);
    }
        // Insert value into new spot
    if (_free[type].empty()) {
        switch (type) {
            case 0: it.setValue(getValue(type, _values0.size()));
                    _values0.push_back(val);
                    break;
            case 1: it.setValue(getValue(type, _values1.size()));
                    _values1.push_back(T1(val));
                    break;
            case 2: it.setValue(getValue(type, _values2.size()));
                    _values2.push_back(T2(val));
                    break;
            case 3: it.setValue(getValue(type, _values3.size()));
                    _values3.push_back(T3(val));
                    break;
            default: assert(false);
        }
    } else {
        it.setValue(getValue(type, _free[type].back()));
        switch (type) {
            case 0: _values0[_free[type].back()] = val; break;
            case 1: _values1[_free[type].back()] = val; break;
            case 2: _values2[_free[type].back()] = val; break;
            case 3: _values3[_free[type].back()] = val; break;
            default: assert(false);
        }
        _free[type].pop_back();
    }
}

} // storage

