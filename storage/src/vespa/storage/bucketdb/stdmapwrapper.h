// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class StdMapWrapper
 * @ingroup bucketdb
 *
 * @brief Wrapper for std::map to add functionality in JudyMultiMap.
 *
 * To remove the need for partial template specialization in lockablemap
 */

#pragma once

#include <map>
#include <vespa/vespalib/util/printable.h>

namespace storage {

template<typename Key, typename Value>
class StdMapWrapper : public std::map<Key, Value>,
                      public vespalib::Printable
{
public:
    StdMapWrapper() {}

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    typename std::map<Key, Value>::iterator find(Key key);

    typename std::map<Key, Value>::iterator find(Key key, bool insert, bool&);

    void insert(Key key, const Value& val, bool&);

    uint32_t getMemoryUsage() const;
};

template<class Key, class Value>
uint32_t
StdMapWrapper<Key, Value>::getMemoryUsage() const
{
    Value val;

    return (32 + sizeof(val)) * this->size();
}

template<class Key, class Value>
void
StdMapWrapper<Key, Value>::print(std::ostream& out,
                                 bool,
                                 const std::string& indent) const
{
    out << "StdMapWrapper(";
    for (typename std::map<Key, Value>::const_iterator i = this->begin();
         i != this->end(); ++i)
    {
        out << "\n" << indent << "  " << "Key: " << i->first << ", Value: "
            << i->second;
    }
    out << ")";
}

template<class Key, class Value>
inline typename std::map<Key, Value>::iterator
StdMapWrapper<Key, Value>::
find(Key key)
{
    bool tmp;
    return find(key, false, tmp);
}

template<class Key, class Value>
inline typename std::map<Key, Value>::iterator
StdMapWrapper<Key, Value>::
find(Key key, bool insertIfNonExisting, bool&)
{
    if (insertIfNonExisting) {
        std::pair<typename std::map<Key, Value>::iterator, bool> result
                = std::map<Key, Value>::insert(std::pair<Key, Value>(key, Value()));
        return result.first;
    } else {
        return std::map<Key, Value>::find(key);
    }
}

template<class Key, class Value>
void
StdMapWrapper<Key, Value>::
insert(Key key, const Value& val, bool&)
{
    this->operator[](key) = val;
}

}

