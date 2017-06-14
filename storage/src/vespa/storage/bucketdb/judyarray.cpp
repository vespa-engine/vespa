// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "judyarray.h"
#include <vespa/vespalib/util/exceptions.h>
#include <iostream>
#include <sstream>

namespace storage {

JudyArray::~JudyArray()
{
    clear();
}

JudyArray::iterator
JudyArray::find(key_type key, bool insertIfNonExisting, bool& preExisted)
{
    Iterator iter(*this, key);
    if (insertIfNonExisting && (iter.end() || iter.key() != key)) {
        preExisted = false;
        insert(key, 0);
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

void
JudyArray::Iterator::setValue(data_type val)
{
    if (_data == 0) {
        throw vespalib::IllegalArgumentException(
            "Cannot set value of end() iterator", VESPA_STRLOC);
    }
    *_data = val;
}

void
JudyArray::Iterator::remove()
{
    if (_data == 0) {
        throw vespalib::IllegalArgumentException(
            "Cannot erase end() iterator", VESPA_STRLOC);
    }
    _parent->erase(_key);
}

bool
JudyArray::operator==(const JudyArray& array) const
{
    if (size() != array.size()) return false;
    for (JudyArray::const_iterator it1 = begin(), it2 = array.begin();
         it1 != end(); ++it1, ++it2)
    {
        if (*it1 != *it2) return false;
    }
    return true;
}

bool
JudyArray::operator<(const JudyArray& array) const
{
    if (size() != array.size()) return (size() < array.size());
    for (JudyArray::const_iterator it1 = begin(), it2 = array.begin();
         it1 != end(); ++it1, ++it2)
    {
        if (*it1 != *it2) return (*it1 < *it2);
    }
    return false;
}

JudyArray::size_type
JudyArray::erase(key_type key)
{
    JError_t err;
    size_type result = JudyLDel(&_judyArray, key, &err);
    if (result == 0 || result == 1) {
        return result;
    }
    std::ostringstream ost;
    ost << "Judy error in erase(" << std::hex << key << "): " << err.je_Errno;
    std::cerr << ost.str() << "\n";
    assert(false);
    return 0;
}


JudyArray::size_type
JudyArray::size() const
{
    key_type lastIndex = 0;
    --lastIndex; // Get last index in size independent way
    return JudyLCount(_judyArray, 0, lastIndex, PJE0);
}

void
JudyArray::swap(JudyArray& other)
{
    void* judyArray = _judyArray; // Save our variables
    _judyArray = other._judyArray; // Assign others to ours
    other._judyArray = judyArray; // Assign temporary to other
}

void
JudyArray::print(std::ostream& out, bool, const std::string& indent) const
{
    out << "JudyArray(";
    for (const_iterator i = begin(); i != end(); ++i) {
        out << "\n" << indent << "  Key: " << i.key()
            << ", Value: " << i.value();
    }
    out << "\n" << indent << ")";
}

JudyArray::ConstIterator::ConstIterator(const JudyArray& arr)
    : _key(0), _data(0), _parent(const_cast<JudyArray*>(&arr)) {}

JudyArray::ConstIterator::ConstIterator(const JudyArray& arr, key_type mykey)
    : _key(mykey), _data(0), _parent(const_cast<JudyArray*>(&arr))
{
    _data = reinterpret_cast<data_type*>(
                JudyLFirst(_parent->_judyArray, &_key, PJE0));
}

void
JudyArray::ConstIterator::print(std::ostream& out, bool, const std::string&) const
{
    if (dynamic_cast<const Iterator*>(this) == 0) {
        out << "Const";
    }
    out << "Iterator(Key: " << _key << ", Valp: " << _data;
    if (_data) out << ", Val: " << *_data;
    out << ")";
}

JudyArray::Iterator::Iterator(JudyArray& arr)
    : ConstIterator(arr) {}

JudyArray::Iterator::Iterator(JudyArray& arr, key_type mykey)
    : ConstIterator(arr, mykey) {}

} // storage
