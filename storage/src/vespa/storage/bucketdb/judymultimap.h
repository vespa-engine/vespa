// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/vespalib/util/array.h>
#include <vector>

namespace storage {

template<class Type0,
         class Type1 = Type0,
         class Type2 = Type1,
         class Type3 = Type2 >
class JudyMultiMap : public vespalib::Printable {
public:
    JudyMultiMap();
    ~JudyMultiMap();

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
#if 0
    void erase(iterator& iter) { iter.remove(); }
#endif

    void insert(key_type key, const Type3& val, bool& preExisted)
    {
        JudyArray::iterator it(_judyArray.find(key, true, preExisted));
        insert(it, val);
    }
    void clear();

    const mapped_type operator[](key_type key);
    size_type getMemoryUsage() const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

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

        bool end() const { return _iterator.end(); }
        key_type key() const { return _iterator.key(); }
        mapped_type value() const;

        const std::pair<key_type, mapped_type>* operator->() const {
            _pair = std::pair<key_type, mapped_type>(_iterator.key(), value());
            return &_pair;
        }

        void print(std::ostream& out, bool verbose, const std::string& indent) const override;

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
    typedef vespalib::Array<Type0> Type0Vector;
    typedef vespalib::Array<Type1> Type1Vector;
    typedef vespalib::Array<Type2> Type2Vector;
    typedef vespalib::Array<Type3> Type3Vector;
    Type0Vector _values0;
    Type1Vector _values1;
    Type2Vector _values2;
    Type3Vector _values3;
    std::vector<std::vector<typename Type0Vector::size_type> > _free;
    friend class Iterator;
    friend class ConstIterator;

    static int getType(JudyArray::data_type index) {
        return index >> (8 * sizeof(JudyArray::data_type) - 2);
    }
    static JudyArray::data_type getIndex(JudyArray::data_type index) {
        return ((index << 2) >> 2);
    }
    static JudyArray::data_type getValue(JudyArray::data_type type,
                                                JudyArray::data_type index)
    {
        return (type << (8 * sizeof(JudyArray::data_type) - 2) | index);
    }
    void insert(JudyArray::iterator& it, const Type3& val);
};

} // storage

