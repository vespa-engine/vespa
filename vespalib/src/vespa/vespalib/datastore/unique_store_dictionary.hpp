// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastore.hpp"
#include "entry_comparator_wrapper.h"
#include "entry_ref_filter.h"
#include "i_compactable.h"
#include "unique_store_add_result.h"
#include "unique_store_dictionary.h"
#include "unique_store_btree_dictionary_read_snapshot.hpp"
#include "unique_store_hash_dictionary_read_snapshot.hpp"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>

namespace vespalib::datastore {

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::UniqueStoreDictionary(std::unique_ptr<EntryComparator> compare)
    : ParentT(),
      UniqueStoreBTreeDictionaryBase<BTreeDictionaryT>(),
      UniqueStoreHashDictionaryBase<HashDictionaryT>(std::move(compare))
{
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::~UniqueStoreDictionary() = default;

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::freeze()
{
    if constexpr (has_btree_dictionary) {
        this->_btree_dict.getAllocator().freeze();
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::assign_generation(generation_t current_gen)
{
    if constexpr (has_btree_dictionary) {
        this->_btree_dict.getAllocator().assign_generation(current_gen);
    }
    if constexpr (has_hash_dictionary) {
        this->_hash_dict.assign_generation(current_gen);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::reclaim_memory(generation_t oldest_used_gen)
{
    if constexpr (has_btree_dictionary) {
        this->_btree_dict.getAllocator().reclaim_memory(oldest_used_gen);
    }
    if constexpr (has_hash_dictionary) {
        this->_hash_dict.reclaim_memory(oldest_used_gen);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
UniqueStoreAddResult
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::add(const EntryComparator &comp,
                                                 std::function<EntryRef(void)> insertEntry)
{
    if constexpr (has_btree_dictionary) {
        using DataType = typename BTreeDictionaryType::DataType;
        auto itr = this->_btree_dict.lowerBound(AtomicEntryRef(), comp);
        if (itr.valid() && !comp.less(EntryRef(), itr.getKey().load_relaxed())) {
            if constexpr (has_hash_dictionary) {
                auto* result = this->_hash_dict.find(comp, EntryRef());
                assert(result != nullptr && result->first.load_relaxed() == itr.getKey().load_relaxed());
            }
            return UniqueStoreAddResult(itr.getKey().load_relaxed(), false);
        } else {
            EntryRef newRef = insertEntry();
            this->_btree_dict.insert(itr, AtomicEntryRef(newRef), DataType());
            if constexpr (has_hash_dictionary) {
                std::function<EntryRef(void)> insert_hash_entry([newRef]() noexcept -> EntryRef { return newRef; });
                auto& add_result = this->_hash_dict.add(comp, newRef, insert_hash_entry);
                assert(add_result.first.load_relaxed() == newRef);
            }
            return UniqueStoreAddResult(newRef, true);
        }
    } else {
        bool inserted = false;
        std::function<EntryRef(void)> insert_hash_entry([&inserted,&insertEntry]() { inserted = true; return insertEntry(); });
        auto& add_result = this->_hash_dict.add(comp, EntryRef(), insert_hash_entry);
        EntryRef newRef = add_result.first.load_relaxed();
        assert(newRef.valid());
        return UniqueStoreAddResult(newRef, inserted);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
EntryRef
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::find(const EntryComparator &comp)
{
    if constexpr (has_btree_dictionary) {
        auto itr = this->_btree_dict.lowerBound(AtomicEntryRef(), comp);
        if (itr.valid() && !comp.less(EntryRef(), itr.getKey().load_relaxed())) {
            if constexpr (has_hash_dictionary) {
                    auto* result = this->_hash_dict.find(comp, EntryRef());
                    assert(result != nullptr && result->first.load_relaxed() == itr.getKey().load_relaxed());
                }
            return itr.getKey().load_relaxed();
        } else {
            if constexpr (has_hash_dictionary) {
                    auto* result = this->_hash_dict.find(comp, EntryRef());
                    assert(result == nullptr);
                }
            return EntryRef();
        }
    } else {
        auto* result = this->_hash_dict.find(comp, EntryRef());
        return (result == nullptr) ? EntryRef() : result->first.load_relaxed();
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::remove(const EntryComparator &comp, EntryRef ref)
{
    assert(ref.valid());
    if constexpr (has_btree_dictionary) {
        auto itr = this->_btree_dict.lowerBound(AtomicEntryRef(ref), comp);
        assert(itr.valid() && itr.getKey().load_relaxed() == ref);
        this->_btree_dict.remove(itr);
    }
    if constexpr (has_hash_dictionary) {
        auto *result = this->_hash_dict.remove(comp, ref);
        assert(result != nullptr && result->first.load_relaxed() == ref);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::move_keys_on_compact(ICompactable &compactable, const EntryRefFilter& compacting_buffers)
{
    if constexpr (has_btree_dictionary) {
        auto itr = this->_btree_dict.begin();
        while (itr.valid()) {
            EntryRef oldRef(itr.getKey().load_relaxed());
            assert(oldRef.valid());
            if (compacting_buffers.has(oldRef)) {
                EntryRef newRef(compactable.move_on_compact(oldRef));
                this->_btree_dict.thaw(itr);
                itr.writeKey(AtomicEntryRef(newRef));
                if constexpr (has_hash_dictionary) {
                    auto result = this->_hash_dict.find(this->_hash_dict.get_default_comparator(), oldRef);
                    assert(result != nullptr && result->first.load_relaxed() == oldRef);
                    result->first.store_release(newRef);
                }
            }
            ++itr;
        }
    } else {
        this->_hash_dict.move_keys_on_compact(compactable, compacting_buffers);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
uint32_t
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_num_uniques() const
{
    if constexpr (has_btree_dictionary) {
        return this->_btree_dict.size();
    } else {
        return this->_hash_dict.size();
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
vespalib::MemoryUsage
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_memory_usage() const
{
    vespalib::MemoryUsage memory_usage;
    if constexpr (has_btree_dictionary) {
        memory_usage.merge(this->_btree_dict.getMemoryUsage());
    }
    if constexpr (has_hash_dictionary) {
        memory_usage.merge(this->_hash_dict.get_memory_usage());
    }
    return memory_usage;
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::build(vespalib::ConstArrayRef<EntryRef> refs,
                                                   vespalib::ConstArrayRef<uint32_t> ref_counts,
                                                   std::function<void(EntryRef)> hold)
{
    assert(refs.size() == ref_counts.size());
    assert(!refs.empty());
    if constexpr (has_btree_dictionary) {
        using DataType = typename BTreeDictionaryType::DataType;
        typename BTreeDictionaryType::Builder builder(this->_btree_dict.getAllocator());
        for (size_t i = 1; i < refs.size(); ++i) {
            if (ref_counts[i] != 0u) {
                builder.insert(AtomicEntryRef(refs[i]), DataType());
            } else {
                hold(refs[i]);
            }
        }
        this->_btree_dict.assign(builder);
    }
    if constexpr (has_hash_dictionary) {
        for (size_t i = 1; i < refs.size(); ++i) {
            if (ref_counts[i] != 0u) {
                EntryRef ref = refs[i];
                std::function<EntryRef(void)> insert_hash_entry([ref]() noexcept -> EntryRef { return ref; });
                auto& add_result = this->_hash_dict.add(this->_hash_dict.get_default_comparator(), ref, insert_hash_entry);
                assert(add_result.first.load_relaxed() == ref);
            } else if constexpr (!has_btree_dictionary) {
                hold(refs[i]);
            }
        }
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::build(vespalib::ConstArrayRef<EntryRef> refs)
{
    if constexpr (has_btree_dictionary) {
        using DataType = typename BTreeDictionaryType::DataType;
        typename BTreeDictionaryType::Builder builder(this->_btree_dict.getAllocator());
        for (const auto& ref : refs) {
            builder.insert(AtomicEntryRef(ref), DataType());
        }
        this->_btree_dict.assign(builder);
    }
    if constexpr (has_hash_dictionary) {
        for (const auto& ref : refs) {
            std::function<EntryRef(void)> insert_hash_entry([ref]() noexcept -> EntryRef { return ref; });
            auto& add_result = this->_hash_dict.add(this->_hash_dict.get_default_comparator(), ref, insert_hash_entry);
            assert(add_result.first.load_relaxed() == ref);
        }
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::build_with_payload(vespalib::ConstArrayRef<EntryRef> refs,
                                                                vespalib::ConstArrayRef<EntryRef> payloads)
{
    assert(refs.size() == payloads.size());
    if constexpr (has_btree_dictionary) {
        using DataType = typename BTreeDictionaryType::DataType;
        typename BTreeDictionaryType::Builder builder(this->_btree_dict.getAllocator());
        for (size_t i = 0; i < refs.size(); ++i) {
            if constexpr (std::is_same_v<DataType, AtomicEntryRef>) {
                builder.insert(AtomicEntryRef(refs[i]), AtomicEntryRef(payloads[i]));
            } else {
                builder.insert(AtomicEntryRef(refs[i]), DataType());
            }
        }
        this->_btree_dict.assign(builder);
    }
    if constexpr (has_hash_dictionary) {
        for (size_t i = 0; i < refs.size(); ++i) {
            EntryRef ref = refs[i];
            std::function<EntryRef(void)> insert_hash_entry([ref]() noexcept -> EntryRef { return ref; });
            auto& add_result = this->_hash_dict.add(this->_hash_dict.get_default_comparator(), ref, insert_hash_entry);
            assert(add_result.first.load_relaxed() == refs[i]);
            add_result.second.store_relaxed(payloads[i]);
        }
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
std::unique_ptr<IUniqueStoreDictionaryReadSnapshot>
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_read_snapshot() const
{
    if constexpr (has_btree_dictionary) {
        return std::make_unique<UniqueStoreBTreeDictionaryReadSnapshot<BTreeDictionaryT>>(this->_btree_dict.getFrozenView());
    }
    if constexpr (has_hash_dictionary) {
        return std::make_unique<UniqueStoreHashDictionaryReadSnapshot<HashDictionaryT>>(this->_hash_dict);
    }
    return std::unique_ptr<IUniqueStoreDictionaryReadSnapshot>();
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
bool
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_has_btree_dictionary() const
{
    return has_btree_dictionary;
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
bool
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_has_hash_dictionary() const
{
    return has_hash_dictionary;
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
vespalib::MemoryUsage
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_btree_memory_usage() const
{
    if constexpr (has_btree_dictionary) {
        return this->_btree_dict.getMemoryUsage();
    }
    return {};
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
vespalib::MemoryUsage
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_hash_memory_usage() const
{
    if constexpr (has_hash_dictionary) {
        return this->_hash_dict.get_memory_usage();
    }
    return {};
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
bool
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::has_held_buffers() const
{
    if constexpr (has_btree_dictionary) {
        if (this->_btree_dict.getAllocator().getNodeStore().has_held_buffers()) {
            return true;
        }
    }
    if constexpr (has_hash_dictionary) {
        if (this->_hash_dict.has_held_buffers()) {
            return true;
        }
    }
    return false;
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::compact_worst(bool compact_btree_dictionary, bool compact_hash_dictionary, const CompactionStrategy& compaction_strategy)
{
    if constexpr (has_btree_dictionary) {
        if (compact_btree_dictionary) {
            this->_btree_dict.compact_worst(compaction_strategy);
        }
    } else {
        (void) compact_btree_dictionary;
    }
    if constexpr (has_hash_dictionary) {
        if (compact_hash_dictionary) {
            this->_hash_dict.compact_worst_shard();
        }
    } else {
        (void) compact_hash_dictionary;
    }
}

}
