// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/generationholder.h>
#include <set>
#include <span>

namespace search {

class CondensedBitVector
{
public:
    using UP = std::unique_ptr<CondensedBitVector>;
    using SP = std::shared_ptr<CondensedBitVector>;
    using Key = uint32_t;
    using KeySet = std::set<Key>;

    virtual ~CondensedBitVector();

    virtual void initializeCountVector(const KeySet & keys, std::span<uint8_t> v) const = 0;
    virtual void addCountVector(const KeySet & keys, std::span<uint8_t> v) const = 0;
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
