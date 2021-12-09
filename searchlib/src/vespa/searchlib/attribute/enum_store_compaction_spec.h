// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/compaction_spec.h>

namespace search              { class IEnumStore;  }
namespace vespalib            { class MemoryUsage; }
namespace vespalib::datastore { class CompactionStrategy; }

namespace search::enumstore {

/*
 * Class describing how to compact an enum store
 */
class EnumStoreCompactionSpec {
    using CompactionSpec = vespalib::datastore::CompactionSpec;
    CompactionSpec _values;
    bool           _btree_dictionary;
    bool           _hash_dictionary;
public:
    EnumStoreCompactionSpec() noexcept
        : _values(),
          _btree_dictionary(false),
          _hash_dictionary(false)
    {
    }

    CompactionSpec get_values() const noexcept { return _values; }
    bool btree_dictionary() const noexcept { return _btree_dictionary; }
    bool hash_dictionary() const noexcept { return _hash_dictionary; }
    vespalib::MemoryUsage update_stat(IEnumStore& enum_store, const vespalib::datastore::CompactionStrategy &compaction_strategy);
};

}
