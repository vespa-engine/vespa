// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_store_loaders.h"
#include "i_enum_store.h"
#include "i_enum_store_dictionary.h"
#include <vespa/vespalib/util/array.hpp>

namespace search::enumstore {

EnumeratedLoaderBase::EnumeratedLoaderBase(IEnumStore& store)
    : _store(store),
      _indexes(),
      _enum_value_remapping()
{
}

EnumeratedLoaderBase::~EnumeratedLoaderBase() = default;

void
EnumeratedLoaderBase::load_unique_values(const void* src, size_t available)
{
    ssize_t sz = _store.load_unique_values(src, available, _indexes);
    assert(static_cast<size_t>(sz) == available);
}

void
EnumeratedLoaderBase::release_enum_indexes()
{
    IndexVector().swap(_indexes);
}

void
EnumeratedLoaderBase::free_unused_values()
{
    _store.free_unused_values();
}

void
EnumeratedLoaderBase::build_enum_value_remapping()
{
    if (!_store.get_dictionary().get_has_btree_dictionary() || _indexes.size() < 2u) {
        return; // No need for unique values to be sorted
    }
    auto comp_up = _store.allocate_comparator();
    auto& comp = *comp_up;
    if (std::adjacent_find(_indexes.begin(), _indexes.end(), [&comp](Index lhs, Index rhs) { return !comp.less(lhs, rhs); }) == _indexes.end()) {
        return; // Unique values are already sorted
    }
    vespalib::Array<std::pair<Index, uint32_t>> sortdata;
    uint32_t enum_value = 0;
    sortdata.reserve(_indexes.size());
    for (auto index : _indexes) {
        sortdata.push_back(std::make_pair(index, enum_value));
        ++enum_value;
    }
    std::sort(sortdata.begin(), sortdata.end(), [&comp](auto lhs, auto rhs) { return comp.less(lhs.first, rhs.first); });
    _enum_value_remapping.resize(_indexes.size());
    enum_value = 0;
    for (auto &entry : sortdata) {
        _indexes[enum_value] = entry.first;
        _enum_value_remapping[entry.second] = enum_value;
        ++enum_value;
    }
    assert(std::adjacent_find(_indexes.begin(), _indexes.end(), [&comp](Index lhs, Index rhs) { return !comp.less(lhs, rhs); }) == _indexes.end());
}

void
EnumeratedLoaderBase::free_enum_value_remapping()
{
    EnumVector().swap(_enum_value_remapping);
}

EnumeratedLoader::EnumeratedLoader(IEnumStore& store)
    : EnumeratedLoaderBase(store),
      _enums_histogram()
{
}

EnumeratedLoader::~EnumeratedLoader() = default;

void
EnumeratedLoader::set_ref_counts()
{
    assert(_enums_histogram.size() == _indexes.size());
    for (uint32_t i = 0; i < _indexes.size(); ++i) {
        _store.set_ref_count(_indexes[i], _enums_histogram[i]);
    }
    EnumVector().swap(_enums_histogram);
}

void
EnumeratedLoader::build_dictionary()
{
    _store.get_dictionary().build(_indexes);
    release_enum_indexes();
}

EnumeratedPostingsLoader::EnumeratedPostingsLoader(IEnumStore& store)
    : EnumeratedLoaderBase(store),
      _loaded_enums(),
      _posting_indexes(),
      _has_btree_dictionary(_store.get_dictionary().get_has_btree_dictionary())
{
}

EnumeratedPostingsLoader::~EnumeratedPostingsLoader() = default;

bool
EnumeratedPostingsLoader::is_folded_change(Index lhs, Index rhs) const
{
    return !_has_btree_dictionary || _store.is_folded_change(lhs, rhs);
}

void
EnumeratedPostingsLoader::set_ref_count(Index idx, uint32_t ref_count)
{
    _store.set_ref_count(idx, ref_count);
}

vespalib::ArrayRef<vespalib::datastore::EntryRef>
EnumeratedPostingsLoader::initialize_empty_posting_indexes()
{
    EntryRefVector(_indexes.size(), EntryRef()).swap(_posting_indexes);
    return _posting_indexes;
}

void
EnumeratedPostingsLoader::build_dictionary()
{
    attribute::LoadedEnumAttributeVector().swap(_loaded_enums);
    _store.get_dictionary().build_with_payload(_indexes, _posting_indexes);
    release_enum_indexes();
    EntryRefVector().swap(_posting_indexes);
}

}
