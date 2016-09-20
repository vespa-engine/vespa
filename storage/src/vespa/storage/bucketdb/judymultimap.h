// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class JudyMultiMap
 *
 * Layer on top of JudyArray, to create a map from the judy array key type,
 * to any of a given set of array types.
 *
 * The value arrays in here all starts with an unused object at index 0.
 * This is because 0 is used as unset value in judyarray, such that we can
 * easily detect if we replace or insert new entry.
 *
 * NB: The order of the template parameters type must be ordered such that
 * the types can include less and less.
 *
 * NB: All iterators are invalidated after writing to judy map.
 *
 * NB: Using JudyArray's insert, one can only detect if the element already
 * existed, if the element didn't have the value 0. Since we don't want to
 * say that values cannot be 0, size is not counted outside of judy array, but
 * rather counts elements in the judy array when asked.
 *
 * @author Haakon Humberset<
 */


#pragma once

#include <vespa/storage/bucketdb/judyarray.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/array.h>
#include <set>
#include <vector>

namespace storage {

template<class Type0,
         class Type1 = Type0,
         class Type2 = Type1,
         class Type3 = Type2 >
class JudyMultiMap : public vespalib::Printable {
public:
    JudyMultiMap()
        : _values0(1), _values1(1), _values2(1), _values3(1), _free(4) {}

    class Iterator;
    class ConstIterator;
    class ValueType;

    typedef Iterator iterator;
    typedef ConstIterator const_iterator;
    typedef JudyArray::key_type key_type;
    typedef Type3 mapped_type;
    typedef std::pair<const key_type, mapped_type> value_type;
    typedef JudyArray::size_type size_type;

    bool operator==(const JudyMultiMap& array) const;
    bool operator<(const JudyMultiMap& array) const;

    /** Warning: Size may be a O(n) function (Unknown implementation in judy) */
    size_type size() const;
    bool empty() const { return (begin() == end()); }

    iterator begin() { return Iterator(*this, 0); }
    iterator end() { return Iterator(*this); }
    const_iterator begin() const { return ConstIterator(*this, 0); }
    const_iterator end() const { return ConstIterator(*this); }

    void swap(JudyMultiMap&);

    const_iterator find(key_type key) const;
    /**
     * Get iterator to value with given key. If non-existing, returns end(),
     * unless insert is true, in which case the element will be created.
     */
    iterator find(key_type key, bool insert, bool& preExisted);
    iterator find(key_type key) { bool b; return find(key, false, b); }

    const_iterator lower_bound(key_type key) const
        { return ConstIterator(*this, key); }
    iterator lower_bound(key_type key) { return Iterator(*this, key); }

    size_type erase(key_type key);
    void erase(iterator& iter) { iter.remove(); }

    void insert(key_type key, const Type3& val, bool& preExisted)
    {
        JudyArray::iterator it(_judyArray.find(key, true, preExisted));
        insert(it, val);
    }
    void clear();

    const mapped_type operator[](key_type key);
    size_type getMemoryUsage() const;

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    class ConstIterator : public vespalib::Printable
    {
    public:
        ConstIterator& operator--() { --_iterator; return *this; }
        ConstIterator& operator++() { ++_iterator; return *this; }

        bool operator==(const ConstIterator &cp) const;
        bool operator!=(const ConstIterator &cp) const {
            return ! (*this == cp);
        }
        value_type operator*() const;

        inline bool end() const { return _iterator.end(); }
        inline key_type key() const { return _iterator.key(); }
        mapped_type value() const;

        const std::pair<key_type, mapped_type>* operator->() const {
            _pair = std::pair<key_type, mapped_type>(_iterator.key(), value());
            return &_pair;
        }

        virtual void print(std::ostream& out,
                           bool verbose, const std::string& indent) const;

    protected:
            // For creating end() iterator
        ConstIterator(const JudyMultiMap&);
            // Create iterator pointing to first element >= key.
        ConstIterator(const JudyMultiMap&, key_type);

        JudyArray::ConstIterator _iterator;
        JudyMultiMap* _parent;
        friend class JudyMultiMap;
        mutable std::pair<key_type, mapped_type> _pair;
    };

    class Iterator : public ConstIterator
    {
    public:
        Iterator& operator--()
        { return static_cast<Iterator&>(ConstIterator::operator--()); }

        Iterator& operator++()
        { return static_cast<Iterator&>(ConstIterator::operator++()); }

        void setValue(const Type3& val);
        void remove();

    private:
        Iterator(JudyMultiMap&);
        Iterator(JudyMultiMap&, key_type key);
        friend class JudyMultiMap;
    };

private:
    JudyArray _judyArray;
    typedef vespalib::Array<Type0, vespalib::DefaultAlloc> Type0Vector;
    typedef vespalib::Array<Type1, vespalib::DefaultAlloc> Type1Vector;
    typedef vespalib::Array<Type2, vespalib::DefaultAlloc> Type2Vector;
    typedef vespalib::Array<Type3, vespalib::DefaultAlloc> Type3Vector;
    Type0Vector _values0;
    Type1Vector _values1;
    Type2Vector _values2;
    Type3Vector _values3;
    std::vector<std::vector<typename Type0Vector::size_type> > _free;
    friend class Iterator;
    friend class ConstIterator;

    inline static int getType(JudyArray::data_type index) {
        return index >> (8 * sizeof(JudyArray::data_type) - 2);
    }
    inline static JudyArray::data_type getIndex(JudyArray::data_type index) {
        return ((index << 2) >> 2);
    }
    inline static JudyArray::data_type getValue(JudyArray::data_type type,
                                                JudyArray::data_type index)
    {
        return (type << (8 * sizeof(JudyArray::data_type) - 2) | index);
    }
    void insert(JudyArray::iterator& it, const Type3& val);
};

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
inline typename JudyMultiMap<T0, T1, T2, T3>::size_type
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
inline typename JudyMultiMap<T0, T1, T2, T3>::const_iterator
JudyMultiMap<T0, T1, T2, T3>::find(key_type key) const
{
    ConstIterator iter(*this, key);
    if (!iter.end() && iter.key() != key) {
        iter = ConstIterator(*this);
    }
    return iter;
}

template<class T0, class T1, class T2, class T3>
inline typename JudyMultiMap<T0, T1, T2, T3>::iterator
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
inline typename JudyMultiMap<T0, T1, T2, T3>::size_type
JudyMultiMap<T0, T1, T2, T3>::erase(key_type key)
{
    JudyArray::iterator it = _judyArray.find(key);
    if (it == _judyArray.end()) return 0;
    _free[getType(it.value())].push_back(getIndex(it.value()));
    _judyArray.erase(key);
    return 1;
}

template<class T0, class T1, class T2, class T3>
inline void
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
inline const typename JudyMultiMap<T0, T1, T2, T3>::mapped_type
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
        default: assert(false);
    }
    return T0(); // Avoid warning of no return
}

template<class T0, class T1, class T2, class T3>
inline typename JudyMultiMap<T0, T1, T2, T3>::size_type
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
inline bool
JudyMultiMap<T0, T1, T2, T3>::
ConstIterator::operator==(const JudyMultiMap::ConstIterator &cp) const
{
    return (_iterator == cp._iterator);
}

template<class T0, class T1, class T2, class T3>
inline typename JudyMultiMap<T0, T1, T2, T3>::value_type
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
            assert(false);
            abort();
    }
}

template<class T0, class T1, class T2, class T3>
inline typename JudyMultiMap<T0, T1, T2, T3>::mapped_type
JudyMultiMap<T0, T1, T2, T3>::ConstIterator::value() const
{
    switch (getType(_iterator.value())) {
        default: assert(false);
        case 0: return _parent->_values0[getIndex(_iterator.value())];
        case 1: return _parent->_values1[getIndex(_iterator.value())];
        case 2: return _parent->_values2[getIndex(_iterator.value())];
        case 3: return _parent->_values3[getIndex(_iterator.value())];
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

template<class T0, class T1, class T2, class T3>
inline void
JudyMultiMap<T0, T1, T2, T3>::Iterator::setValue(const T3& val)
{
    if (this->_iterator.end()) {
        throw vespalib::IllegalArgumentException(
            "Cannot set value of end() iterator", VESPA_STRLOC);
    }
    insert(this->iterator, val);
}

template<class T0, class T1, class T2, class T3>
inline void
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

