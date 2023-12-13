// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multistringpostattribute.hpp"
#include "postinglistsearchcontext.hpp"

namespace search {

IEnumStore::Index
StringEnumIndexMapper::map(IEnumStore::Index original) const
{
    return _dictionary.remap_index(original);
}

template class MultiValueStringPostingAttributeT<EnumAttribute<StringAttribute>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueStringPostingAttributeT<EnumAttribute<StringAttribute>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;

} // namespace search

