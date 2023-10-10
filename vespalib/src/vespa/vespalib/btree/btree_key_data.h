// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib::btree {

/**
 * Empty class to use as DataT template parameter for BTree classes to
 * indicate that leaf nodes have no data (similar to std::set having less
 * information than std::map).
 */
class BTreeNoLeafData
{
public:
    static BTreeNoLeafData _instance;
};


template <typename KeyT, typename DataT>
class BTreeKeyData
{
public:
    using KeyType = KeyT;
    using DataType = DataT;

    KeyT _key;
    DataT _data;

    BTreeKeyData() noexcept
        : _key(),
          _data()
    {}

    BTreeKeyData(const KeyT &key, const DataT &data) noexcept
        : _key(key),
          _data(data)
    {}

    void setData(const DataT &data) { _data = data; }
    const DataT &getData() const { return _data; }

    /**
     * This operator only works when using direct keys.  References to
     * externally stored keys will not be properly sorted.
     */
    bool operator<(const BTreeKeyData &rhs) const {
        return _key < rhs._key;
    }
};


template <typename KeyT>
class BTreeKeyData<KeyT, BTreeNoLeafData>
{
public:
    using KeyType = KeyT;
    using DataType = BTreeNoLeafData;

    KeyT _key;

    BTreeKeyData() noexcept : _key() {}

    BTreeKeyData(const KeyT &key, const BTreeNoLeafData &) noexcept
        : _key(key)
    {
    }

    void setData(const BTreeNoLeafData &) { }
    const BTreeNoLeafData &getData() const { return BTreeNoLeafData::_instance; }

    /**
     * This operator only works when using direct keys.  References to
     * externally stored keys will not be properly sorted.
     */
    bool operator<(const BTreeKeyData &rhs) const {
        return _key < rhs._key;
    }
};

extern template class BTreeKeyData<uint32_t, uint32_t>;
extern template class BTreeKeyData<uint32_t, int32_t>;

} // namespace vespalib::btree
