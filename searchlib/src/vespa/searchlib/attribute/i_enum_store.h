// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_types.h"
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <memory>

namespace vespalib {
    class AddressSpace;
    class MemoryUsage;
}

namespace vespalib::datastore {
    class CompactionSpec;
    class CompactionStrategy;
    class DataStoreBase;
    class EntryComparator;
    template <typename> class UniqueStoreRemapper;
    template <typename> class UniqueStoreEnumerator;
}

namespace search::enumstore {
    class EnumeratedLoader;
    class EnumeratedPostingsLoader;
}
namespace search {

class BufferWriter;
class IEnumStoreDictionary;

/**
 * Interface for an enum store that is independent of the data type stored.
 */
class IEnumStore {
public:
    using AtomicIndex = enumstore::AtomicIndex;
    using Index = enumstore::Index;
    using InternalIndex = enumstore::InternalIndex;
    using IndexVector = enumstore::IndexVector;
    using CompactionSpec = vespalib::datastore::CompactionSpec;
    using CompactionStrategy = vespalib::datastore::CompactionStrategy;
    using EnumHandle = enumstore::EnumHandle;
    using EnumVector = enumstore::EnumVector;
    using EnumIndexRemapper = vespalib::datastore::UniqueStoreRemapper<InternalIndex>;
    using Enumerator = vespalib::datastore::UniqueStoreEnumerator<InternalIndex>;

    using IndexList = std::vector<Index>;

    virtual ~IEnumStore() = default;

    virtual void write_value(BufferWriter& writer, Index idx) const = 0;
    virtual ssize_t load_unique_values(const void* src, size_t available, IndexVector& idx) = 0;
    virtual void set_ref_count(Index idx, uint32_t ref_count) = 0;
    virtual void free_value_if_unused(Index idx, IndexList& unused) = 0;
    virtual void free_unused_values() = 0;
    virtual bool is_folded_change(Index idx1, Index idx2) const = 0;
    virtual IEnumStoreDictionary& get_dictionary() = 0;
    virtual const IEnumStoreDictionary& get_dictionary() const = 0;
    virtual uint32_t get_num_uniques() const = 0;
    virtual vespalib::MemoryUsage get_values_memory_usage() const = 0;
    virtual vespalib::AddressSpace get_values_address_space_usage() const = 0;
    virtual vespalib::MemoryUsage get_dictionary_memory_usage() const = 0;
    virtual vespalib::MemoryUsage update_stat(const CompactionStrategy& compaction_strategy) = 0;
    virtual std::unique_ptr<EnumIndexRemapper> consider_compact_values(const CompactionStrategy& compaction_strategy) = 0;
    virtual std::unique_ptr<EnumIndexRemapper> compact_worst_values(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy) = 0;
    virtual bool consider_compact_dictionary(const CompactionStrategy& compaction_strategy) = 0;
    virtual uint64_t get_compaction_count() const = 0;
    // Should only be used by unit tests.
    virtual void inc_compaction_count() = 0;

    enumstore::EnumeratedLoader make_enumerated_loader();
    enumstore::EnumeratedPostingsLoader make_enumerated_postings_loader();

    virtual std::unique_ptr<Enumerator> make_enumerator() const = 0;
    virtual std::unique_ptr<vespalib::datastore::EntryComparator> allocate_comparator() const = 0;
};

}
