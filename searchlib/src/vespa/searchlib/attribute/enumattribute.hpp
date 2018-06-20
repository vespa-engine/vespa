// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/searchlib/attribute/enumattribute.h>
#include <vespa/searchlib/attribute/enumstore.hpp>

namespace search {

template <typename B>
EnumAttribute<B>::
EnumAttribute(const vespalib::string &baseFileName,
              const AttributeVector::Config &cfg)
    : B(baseFileName, cfg),
      _enumStore(0, cfg.fastSearch())
{
    this->setEnum(true);
}

template <typename B>
EnumAttribute<B>::~EnumAttribute()
{
}

template <typename B>
void EnumAttribute<B>::fillEnum(LoadedVector & loaded)
{
    typename EnumStore::Builder builder;
    if (!loaded.empty()) {
        typename LoadedVector::Type v = loaded.read();
        LoadedValueType prev = v.getValue();
        uint32_t prevRefCount(0);
        EnumIndex index = builder.insert(v.getValue(), v._pidx.ref());
        for(size_t i(0), m(loaded.size()); i < m; ++i, loaded.next()) {
            v = loaded.read();
            if (EnumStore::ComparatorType::compare(prev, v.getValue()) != 0) {
                builder.updateRefCount(prevRefCount);
                index = builder.insert(v.getValue(), v._pidx.ref());
                prev = v.getValue();
                prevRefCount = 1;
            } else {
                prevRefCount++;
            }
            v.setEidx(index);
            loaded.write(v);
        }
        builder.updateRefCount(prevRefCount);
    }
    _enumStore.reset(builder);
    this->setEnumMax(_enumStore.getLastEnum());
}


template <typename B>
void
EnumAttribute<B>::fillEnum0(const void *src,
                            size_t srcLen,
                            EnumIndexVector &eidxs)
{
    ssize_t sz = _enumStore.deserialize(src, srcLen, eidxs);
    assert(static_cast<size_t>(sz) == srcLen);
    (void) sz;
    this->setEnumMax(_enumStore.getLastEnum());
}


template <typename B>
void
EnumAttribute<B>::fixupEnumRefCounts(
        const EnumVector &enumHist)
{
    _enumStore.fixupRefCounts(enumHist);
}


template <typename B>
uint64_t
EnumAttribute<B>::getUniqueValueCount() const
{
    return _enumStore.getNumUniques();
}



template <typename B>
void
EnumAttribute<B>::insertNewUniqueValues(EnumStoreBase::IndexVector & newIndexes)
{
    UniqueSet newUniques;

    // find new unique strings
    for (const auto & data : this->_changes) {
        considerAttributeChange(data, newUniques);
    }

    uint64_t extraBytesNeeded = 0;
    for (const auto & data : newUniques) {
        extraBytesNeeded += _enumStore.getEntrySize(data.raw());
    }

    do {
        // perform compaction on EnumStore if necessary
        if (extraBytesNeeded > this->_enumStore.getRemaining() ||
            this->_enumStore.getPendingCompact()) {
            this->removeAllOldGenerations();
            this->_enumStore.clearPendingCompact();
            if (!this->_enumStore.performCompaction(extraBytesNeeded)) {
                // fallback to resize strategy
                this->_enumStore.fallbackResize(extraBytesNeeded);
                if (extraBytesNeeded > this->_enumStore.getRemaining()) {
                    HDR_ABORT("Cannot fallbackResize enumStore");
                }
                break;  // fallback resize performed instead of compaction.
            }

            // update underlying structure with new EnumIndex values.
            reEnumerate();
            // Clear scratch enumeration
            for (auto & data : this->_changes) {
                data._enumScratchPad = ChangeBase::UNSET_ENUM;
            }

            // clear mapping from old enum value to new index
            _enumStore.clearIndexMap();
        }
    } while (0);

    // insert new unique values in EnumStore
    for (const auto & data : newUniques) {
        EnumIndex idx;
        _enumStore.addEnum(data.raw(), idx);
        newIndexes.push_back(idx);
    }
}


template <typename B>
AddressSpace
EnumAttribute<B>::getEnumStoreAddressSpaceUsage() const
{
    return _enumStore.getAddressSpaceUsage();
}

} // namespace search


