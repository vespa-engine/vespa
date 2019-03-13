// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class JudyArray
 *
 * Implements a pair associative container on top of a judy array.
 *
 * NB: All iterators are invalidated after writing to judy array.
 *
 * NB: Using JudyArray's insert, one can only detect if the element already
 * existed, if the element didn't have the value 0. Since we don't want to
 * say that values cannot be 0, size is not counted outside of judy array, but
 * rather counts elements in the judy array when asked.
 *
 * @author Haakon Humberset
 */

#pragma once

#include <vespa/vespalib/util/printable.h>
#include <cinttypes>
#include <Judy.h>
#include <cassert>

namespace storage {

class JudyArray : public vespalib::Printable
{
    JudyArray(const JudyArray&); // Deny copying
    JudyArray& operator=(const JudyArray&);

public:
    class Iterator;
    class ConstIterator;

    typedef Iterator iterator;
    typedef ConstIterator const_iterator;
    typedef unsigned long key_type;
    typedef unsigned long data_type;
    typedef std::pair<const key_type, data_type> value_type;
    typedef size_t size_type;
    typedef value_type& reference;
    typedef const value_type& const_reference;
    typedef value_type* pointer;
    typedef int difference_type;

    JudyArray() : _judyArray(NULL) {}
    virtual ~JudyArray();

    bool operator==(const JudyArray& array) const;
    bool operator!=(const JudyArray& array) const {
        return ! (*this == array);
    }
    bool operator<(const JudyArray& array) const;

    /** Warning: Size may be a O(n) function (Unknown implementation in judy) */
    size_type size() const;
    bool empty() const { return (begin() == end()); }

    iterator begin() { return Iterator(*this, 0); }
    iterator end() { return Iterator(*this); }
    const_iterator begin() const { return ConstIterator(*this, 0); }
    const_iterator end() const { return ConstIterator(*this); }

    void swap(JudyArray&);

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

    void insert(key_type key, data_type val);
    void clear();

    data_type& operator[](key_type key);
    size_type getMemoryUsage() const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    class ConstIterator : public vespalib::Printable
    {
    public:
        ConstIterator& operator--();
        ConstIterator& operator++();

        bool operator==(const ConstIterator &cp) const;
        bool operator!=(const ConstIterator &cp) const {
            return ! (*this == cp);
        }
        value_type operator*() const { return value_type(_key, *_data); }

        bool end() const { return (_data == 0); }
        key_type key() const { return _key; }
        data_type value() const { return *_data; }

        void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    protected:
            // For creating end() iterator
        ConstIterator(const JudyArray&);
            // Create iterator pointing to first element >= key.
        ConstIterator(const JudyArray&, key_type);

        key_type _key; // Key iterator currently points to
        data_type* _data; // Pointer to member pointed to, or 0 if end().
        JudyArray* _parent;
        friend class JudyArray;
    };

    class Iterator : public ConstIterator
    {
    public:
        Iterator& operator--()
        { return static_cast<Iterator&>(ConstIterator::operator--()); }

        Iterator& operator++()
        { return static_cast<Iterator&>(ConstIterator::operator++()); }

        void setValue(data_type val);
        void remove();

    private:
        Iterator(JudyArray&);
        Iterator(JudyArray&, key_type key);
        friend class JudyArray;
    };

private:
    void *_judyArray;
    friend class Iterator;
    friend class ConstIterator;
};

inline JudyArray::const_iterator
JudyArray::find(key_type key) const
{
    ConstIterator iter(*this, key);
    if (!iter.end() && iter.key() != key) {
        iter = ConstIterator(*this);
    }
    return iter;
}

inline void
JudyArray::insert(key_type key, data_type val)
{
    data_type* valp = reinterpret_cast<data_type*>(
                            JudyLIns(&_judyArray, key, PJE0));
    *valp = val;
}

inline void
JudyArray::clear()
{
    JudyLFreeArray(&_judyArray, PJE0);
}

inline JudyArray::data_type&
JudyArray::operator[](key_type key)
{
    data_type* valp = reinterpret_cast<data_type*>(
                            JudyLGet(_judyArray, key, PJE0));
    if (valp == 0) {
        valp = reinterpret_cast<data_type*>(JudyLIns(&_judyArray, key, PJE0));
        *valp = 0;
    }
    return *valp;
}

inline JudyArray::size_type
JudyArray::getMemoryUsage() const
{
    return JudyLMemUsed(_judyArray);
}

inline JudyArray::ConstIterator&
JudyArray::ConstIterator::operator--() // Prefix
{
    if (!_data) {
        _data = reinterpret_cast<data_type*>(
                JudyLLast(_parent->_judyArray, &_key, PJE0));
    } else {
        _data = reinterpret_cast<data_type*>(
                JudyLPrev(_parent->_judyArray, &_key, PJE0));
    }
    return *this;
}

inline JudyArray::ConstIterator&
JudyArray::ConstIterator::operator++() // Prefix
{
    _data = reinterpret_cast<data_type*>(
                JudyLNext(_parent->_judyArray, &_key, PJE0));
    return *this;
}

inline bool
JudyArray::ConstIterator::operator==(const JudyArray::ConstIterator &cp) const
{
    return (_data == cp._data);
}

} // storage
