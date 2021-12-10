// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_store_compaction_spec.h"
#include "i_enum_store.h"
#include "i_enum_store_dictionary.h"
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/util/address_space.h>

namespace search::enumstore {

using vespalib::datastore::CompactionStrategy;

vespalib::MemoryUsage
EnumStoreCompactionSpec::update_stat(IEnumStore& enum_store, const CompactionStrategy& compaction_strategy)
{
    auto values_memory_usage = enum_store.get_values_memory_usage();
    auto values_address_space_usage = enum_store.get_values_address_space_usage();
    _values = compaction_strategy.should_compact(values_memory_usage, values_address_space_usage);
    auto& dict = enum_store.get_dictionary();
    auto dictionary_btree_usage = dict.get_btree_memory_usage();
    _btree_dictionary = compaction_strategy.should_compact_memory(dictionary_btree_usage);
    auto dictionary_hash_usage = dict.get_hash_memory_usage();
    _hash_dictionary = compaction_strategy.should_compact_memory(dictionary_hash_usage);
    auto retval = values_memory_usage;
    retval.merge(dictionary_btree_usage);
    retval.merge(dictionary_hash_usage);
    return retval;
}

}
