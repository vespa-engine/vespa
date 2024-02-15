// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_index_saver.hpp"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>

namespace search::predicate {

template class SimpleIndexSaver<vespalib::datastore::EntryRef>;

}
