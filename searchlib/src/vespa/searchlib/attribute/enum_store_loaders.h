// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_types.h"
#include "loadedenumvalue.h"

namespace search { class IEnumStore; }

namespace search::enumstore {

/**
 * Base helper class used to load an enum store from enumerated save files.
 */
class EnumeratedLoaderBase {
protected:
    IEnumStore& _store;
    IndexVector _indexes;
    EnumVector  _enum_value_remapping; // Empty if saved unique values are sorted.

    void release_enum_indexes();
public:
    EnumeratedLoaderBase(IEnumStore& store);
    EnumeratedLoaderBase(const EnumeratedLoaderBase &) = delete;
    EnumeratedLoaderBase & operator =(const EnumeratedLoaderBase &) = delete;
    EnumeratedLoaderBase(EnumeratedLoaderBase &&) = delete;
    EnumeratedLoaderBase & operator =(EnumeratedLoaderBase &&) = delete;
    ~EnumeratedLoaderBase();
    const IndexVector& get_enum_indexes() const { return _indexes; }
    const EnumVector& get_enum_value_remapping() const noexcept { return _enum_value_remapping; }
    void load_unique_values(const void* src, size_t available);
    void build_enum_value_remapping();
    void free_enum_value_remapping();
    void free_unused_values();
};

/**
 * Helper class used to load an enum store from enumerated save files.
 */
class EnumeratedLoader : public EnumeratedLoaderBase {
private:
    EnumVector _enums_histogram;

public:
    EnumeratedLoader(IEnumStore& store);
    EnumeratedLoader(const EnumeratedLoader &) = delete;
    EnumeratedLoader & operator =(const EnumeratedLoader &) = delete;
    EnumeratedLoader(EnumeratedLoader &&) = delete;
    EnumeratedLoader & operator =(EnumeratedLoader &&) = delete;
    ~EnumeratedLoader();
    EnumVector& get_enums_histogram() { return _enums_histogram; }
    void allocate_enums_histogram() {
        EnumVector(_indexes.size(), 0).swap(_enums_histogram);
    }
    void set_ref_counts();
    void build_dictionary();
};

/**
 * Helper class used to load an enum store (with posting lists) from enumerated save files.
 */
class EnumeratedPostingsLoader : public EnumeratedLoaderBase {
private:
    using EntryRef = vespalib::datastore::EntryRef;
    using EntryRefVector = std::vector<EntryRef, vespalib::allocator_large<EntryRef>>;
    attribute::LoadedEnumAttributeVector _loaded_enums;
    EntryRefVector                       _posting_indexes;
    bool                                 _has_btree_dictionary;

public:
    EnumeratedPostingsLoader(IEnumStore& store);
    EnumeratedPostingsLoader(const EnumeratedPostingsLoader &) = delete;
    EnumeratedPostingsLoader & operator =(const EnumeratedPostingsLoader &) = delete;
    EnumeratedPostingsLoader(EnumeratedPostingsLoader &&) = delete;
    EnumeratedPostingsLoader & operator =(EnumeratedPostingsLoader &&) = delete;
    ~EnumeratedPostingsLoader();
    attribute::LoadedEnumAttributeVector& get_loaded_enums() { return _loaded_enums; }
    void reserve_loaded_enums(size_t num_values) {
        _loaded_enums.reserve(num_values);
    }
    void sort_loaded_enums() {
        attribute::sortLoadedByEnum(_loaded_enums);
    }
    bool is_folded_change(Index lhs, Index rhs) const;
    void set_ref_count(Index idx, uint32_t ref_count);
    vespalib::ArrayRef<EntryRef> initialize_empty_posting_indexes();
    void build_dictionary();
    void build_empty_dictionary();
};

}
