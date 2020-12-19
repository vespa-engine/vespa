// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_index.hpp"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.predicate.simple_index");

namespace search::predicate {
 namespace simpleindex {

bool log_enabled() {
    return LOG_WOULD_LOG(debug);
}

void log_debug(vespalib::string &str) {
    LOG(debug, "%s", str.c_str());
}

} // namespace simpleindex

template class SimpleIndex<vespalib::datastore::EntryRef>;

}

