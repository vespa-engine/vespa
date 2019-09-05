// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_store_loaders.h"
#include "i_enum_store.h"
#include <vespa/vespalib/util/array.hpp>

namespace search::enumstore {

EnumeratedLoaderBase::EnumeratedLoaderBase(IEnumStore& store)
    : _store(store),
      _indexes()
{
}

void
EnumeratedLoaderBase::load_unique_values(const void* src, size_t available)
{
    ssize_t sz = _store.load_unique_values(src, available, _indexes);
    assert(static_cast<size_t>(sz) == available);
}

EnumeratedLoader::EnumeratedLoader(IEnumStore& store)
    : EnumeratedLoaderBase(store),
      _enums_histogram()
{
}

void
EnumeratedLoader::set_ref_counts()
{
    _store.fixupRefCounts(_enums_histogram);
}

EnumeratedPostingsLoader::EnumeratedPostingsLoader(IEnumStore& store)
    : EnumeratedLoaderBase(store),
      _loaded_enums()
{
}

bool
EnumeratedPostingsLoader::is_folded_change(const Index& lhs, const Index& rhs) const
{
    return _store.foldedChange(lhs, rhs);
}

void
EnumeratedPostingsLoader::set_ref_count(Index idx, uint32_t ref_count)
{
    _store.fixupRefCount(idx, ref_count);
}

void
EnumeratedPostingsLoader::free_unused_enums()
{
    _store.freeUnusedEnums();
}

}
