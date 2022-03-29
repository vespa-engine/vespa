// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/generationholder.h>
#include <vespa/vespalib/util/arrayref.h>
#include <set>

namespace search {

class CondensedBitVector
{
public:
    typedef std::unique_ptr<CondensedBitVector> UP;
    typedef std::shared_ptr<CondensedBitVector> SP;
    typedef uint32_t Key;
    typedef std::set<Key> KeySet;
    typedef vespalib::ArrayRef<uint8_t> CountVector;

    virtual ~CondensedBitVector();

    virtual void initializeCountVector(const KeySet & keys, CountVector & v) const = 0;
    virtual void addCountVector(const KeySet & keys, CountVector & v) const = 0;
    virtual void set(Key key, uint32_t index, bool v) = 0;
    virtual bool get(Key key, uint32_t index) const = 0;
    virtual void clearIndex(uint32_t index) = 0;
    virtual size_t getKeyCapacity() const = 0;
    /*
     * getCapacity() should be called from writer only.
     * Const type qualifier removed to prevent call from readers.
     */
    virtual size_t getCapacity() = 0;
    /*
     * getSize() should be called from writer only.
     * Const type qualifier removed to prevent call from readers.
     */
    virtual size_t getSize() = 0;
    virtual void adjustDocIdLimit(uint32_t docId) = 0;
    bool hasKey(Key key) const { return key < getKeyCapacity(); }
    void addKey(Key key) const;
    static CondensedBitVector::UP create(size_t size, vespalib::GenerationHolder &genHolder);
};

}
