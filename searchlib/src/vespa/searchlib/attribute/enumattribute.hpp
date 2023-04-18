// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumattribute.h"
#include "address_space_components.h"
#include "enumstore.hpp"
#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/searchcommon/attribute/config.h>

namespace search {

template <typename B>
EnumAttribute<B>::
EnumAttribute(const vespalib::string &baseFileName,
              const AttributeVector::Config &cfg)
    : B(baseFileName, cfg),
      _enumStore(cfg.fastSearch(), cfg.get_dictionary_config(), this->get_memory_allocator(), this->_defaultValue._data.raw())
{
    this->setEnum(true);
}

template <typename B>
EnumAttribute<B>::~EnumAttribute() = default;

template <typename B>
void EnumAttribute<B>::load_enum_store(LoadedVector& loaded)
{
    if constexpr(!std::is_same_v<LoadedVector, NoLoadedVector>) {
        auto loader = _enumStore.make_non_enumerated_loader();
        if (!loaded.empty()) {
            auto value = loaded.read();
            typename B::LoadedValueType prev = value.getValue();
            uint32_t prevRefCount(0);
            EnumIndex index = loader.insert(value.getValue(), value._pidx.ref());
            for (size_t i(0), m(loaded.size()); i < m; ++i, loaded.next()) {
                value = loaded.read();
                if (!EnumStore::ComparatorType::equal_helper(prev, value.getValue())) {
                    loader.set_ref_count_for_last_value(prevRefCount);
                    index = loader.insert(value.getValue(), value._pidx.ref());
                    prev = value.getValue();
                    prevRefCount = 1;
                } else {
                    assert(prevRefCount < std::numeric_limits<uint32_t>::max());
                    prevRefCount++;
                }
                value.setEidx(index);
                loaded.write(value);
            }
            loader.set_ref_count_for_last_value(prevRefCount);
        }
        loader.build_dictionary();
        _enumStore.setup_default_value_ref();
    }
}

template <typename B>
uint64_t
EnumAttribute<B>::getUniqueValueCount() const
{
    return _enumStore.get_num_uniques();
}

template <typename B>
void
EnumAttribute<B>::insertNewUniqueValues(EnumStoreBatchUpdater& updater)
{
    // find and insert new unique strings
    for (const auto & data : this->_changes.getInsertOrder()) {
        considerAttributeChange(data, updater);
    }
}

template <typename B>
vespalib::MemoryUsage
EnumAttribute<B>::getEnumStoreValuesMemoryUsage() const
{
    return _enumStore.get_dynamic_values_memory_usage();
}

template <typename B>
void
EnumAttribute<B>::populate_address_space_usage(AddressSpaceUsage& usage) const
{
    B::populate_address_space_usage(usage);
    usage.set(AddressSpaceComponents::enum_store, _enumStore.get_values_address_space_usage());
}

} // namespace search
