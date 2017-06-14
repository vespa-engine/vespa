// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_index.hpp"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.simple_index");

namespace search {
namespace predicate {
namespace simpleindex {

bool log_enabled() {
    return LOG_WOULD_LOG(debug);
}

void log_debug(vespalib::string &str) {
    LOG(debug, "%s", str.c_str());
}

} // namespace simpleindex

template class SimpleIndex<datastore::EntryRef>;

}  // namespace predicate
} // namespace search

