// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @file hashmap.cpp
 * @brief simple hashmap implementation
 * @author H�vard Pettersen
 * @version $Id$
 * @date 2006/03/16
 **/

#include "hashmap.h"

namespace vespalib {
#ifndef IAM_DOXYGEN

const uint32_t HashMapData::sizeStepsSize = 30;
uint32_t HashMapData::sizeSteps[sizeStepsSize] =
{
    3,
    7,
    13,
    31,
    61,
    127,
    251,
    509,
    1021,
    2039,
    4093,
    8191,
    16381,
    32749,
    65521,
    131071,
    262139,
    524287,
    1048573,
    2097143,
    4194301,
    8388593,
    16777213,
    33554393,
    67108859,
    134217689,
    268435399,
    536870909,
    1073741789,
    2147483647
};

#endif //#ifndef IAM_DOXYGEN


/**
 * @fn explicit HashMap<T>::HashMap(const T &empty, uint32_t minBuckets = 50)
 * @brief Constructor.
 * @param empty value used for non-existing entries
 * @param minBuckets minimum number of hash buckets
 **/

/**
 * @fn HashMap<T>::~HashMap()
 * @brief Destructor.
 **/

/**
 * @fn void HashMap<T>::clear()
 * @brief Remove all entries.
 **/

/**
 * @fn T HashMap<T>::set(const char *key, const T &value)
 * @brief Set a value.
 *
 * The key 'key' will map to 'value'.
 * If the number of entries in the hashmap exceeds 3/5 the number of buckets,
 * the map will resize to the next bigger bucket size.
 *
 * @return old value for key if set, otherwise 'empty' from constructor
 * @param key hash key
 * @param value new value for key
 **/

/**
 * @fn bool HashMap<T>::isSet(const char *key) const
 * @brief Check if a key is set.
 * @return true if the given key was set
 * @param key hash key
 **/

/**
 * @fn const T &HashMap<T>::get(const char *key) const
 * @brief Get a value
 * @return the value that 'key' maps to if set,
 *         otherwise 'empty' from constructor
 * @param key hash key
 **/

/**
 * @fn T HashMap<T>::remove(const char *key)
 * @brief Remove a value.
 * @return old value for the given key
 * @param key hash key
 **/

/**
 * @fn const T& HashMap<T>::operator[](const char *key) const
 * @brief Get a value
 * @return the value that 'key' maps to, or a copy of empty if no mapping
 * @param key hash key
 **/

/**
 * @fn HashMap<T>::Iterator HashMap<T>::iterator() const
 * @brief Obtain an unsafe iterator for this hash map.
 * @return hash map iterator
 **/

/**
 * @fn uint32_t HashMap<T>::size() const
 * @brief Obtain the number of entries in this map.
 * @return number of entries
 **/

/**
 * @fn bool HashMap<T>::isEmpty() const
 * @brief Check for emptiness.
 * @return Whether this map is empty
 **/

/**
 * @fn uint32_t HashMap<T>::buckets() const
 * @brief Obtain the number of hash buckets.
 * @return the number of hash buckets
 **/

//-----------------------------------------------------------------------------

/**
 * @fn HashMap<T>::Iterator::Iterator(const Iterator &src)
 * @brief Copy constructor.
 * @param src copy this
 **/

/**
 * @fn HashMap<T>::Iterator& HashMap<T>::Iterator::operator=(const Iterator &src)
 * @brief Assignment.
 * @param src copy this
 **/

/**
 * @fn bool HashMap<T>::Iterator::valid() const
 * @brief Check if current entry is valid.
 *
 * This method returns true until the iterator has been stepped after
 * the last entry in the hash map.
 *
 * @return true if the current entry is valid
 **/

/**
 * @fn const char *HashMap<T>::Iterator::key() const
 * @brief Get current key.
 *
 * This method should only be called when the valid method returns
 * true.
 *
 * @return current key
 **/

/**
 * @fn const T &HashMap<T>::Iterator::value() const
 * @brief Get current value.
 *
 * This method should only be called when the valid method returns
 * true.
 *
 * @return current value
 **/

/**
 * @fn void HashMap<T>::Iterator::next()
 * @brief Step this iterator.
 *
 * This method should only be called when the valid method returns
 * true.
 **/

/**
 * @brief Calculate hash value.
 *
 * This is the hash function used by the HashMap class.
 * The hash function is inherited from Fastserver4 / FastLib / pandora.
 * @param str input string, zero terminated
 * @return hash value of input
 **/
size_t
hashValue(const char *str)
{
    size_t res = 0;
    unsigned const char *pt = (unsigned const char *) str;
    while (*pt != 0) {
        res = (res << 7) + (res >> 25) + *pt++;
    }
    return res;
}

/**
 * @brief Calculate hash value.
 *
 * This is the hash function used by the HashMap class.
 * The hash function is inherited from Fastserver4 / FastLib / pandora.
 * @param buf input buffer
 * @param sz input buffer size
 * @return hash value of input
 **/
size_t
hashValue(const void * buf, size_t sz)
{
    size_t res = 0;
    unsigned const char *pt = (unsigned const char *) buf;
    for (size_t i(0); i < sz; i++) {
        res = (res << 7) + (res >> 25) + pt[i];
    }
    return res;
}


} // namespace vespalib
