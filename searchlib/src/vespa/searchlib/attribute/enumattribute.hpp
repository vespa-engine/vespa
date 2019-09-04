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
      _enumStore(cfg.fastSearch())
{
    this->setEnum(true);
}

template <typename B>
EnumAttribute<B>::~EnumAttribute()
{
}

template <typename B>
void EnumAttribute<B>::load_enum_store(LoadedVector& loaded)
{
    if constexpr(!std::is_same_v<LoadedVector, NoLoadedVector>) {
        auto loader = _enumStore.make_non_enumerated_loader();
        if (!loaded.empty()) {
            auto value = loaded.read();
            LoadedValueType prev = value.getValue();
            uint32_t prevRefCount(0);
            EnumIndex index = loader.insert(value.getValue(), value._pidx.ref());
            for (size_t i(0), m(loaded.size()); i < m; ++i, loaded.next()) {
                value = loaded.read();
                if (EnumStore::ComparatorType::compare(prev, value.getValue()) != 0) {
                    loader.set_ref_count_for_last_value(prevRefCount);
                    index = loader.insert(value.getValue(), value._pidx.ref());
                    prev = value.getValue();
                    prevRefCount = 1;
                } else {
                    prevRefCount++;
                }
                value.setEidx(index);
                loaded.write(value);
            }
            loader.set_ref_count_for_last_value(prevRefCount);
        }
        loader.build_dictionary();
    }
}

template <typename B>
uint64_t
EnumAttribute<B>::getUniqueValueCount() const
{
    return _enumStore.getNumUniques();
}

template <typename B>
void
EnumAttribute<B>::insertNewUniqueValues(EnumStoreBatchUpdater& updater)
{
    UniqueSet newUniques;

    // find new unique strings
    for (const auto & data : this->_changes) {
        considerAttributeChange(data, newUniques);
    }

    // insert new unique values in EnumStore
    for (const auto & data : newUniques) {
        updater.add(data.raw());
    }
}

template <typename B>
vespalib::MemoryUsage
EnumAttribute<B>::getEnumStoreValuesMemoryUsage() const
{
    return _enumStore.getValuesMemoryUsage();
}

template <typename B>
vespalib::AddressSpace
EnumAttribute<B>::getEnumStoreAddressSpaceUsage() const
{
    return _enumStore.getAddressSpaceUsage();
}

} // namespace search


