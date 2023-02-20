// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/address_space.h>

namespace search {

/**
 * A set of components in an attribute vector that use address space.
 */
class AddressSpaceComponents {
public:
    static vespalib::AddressSpace default_enum_store_usage();
    static vespalib::AddressSpace default_multi_value_usage();
    static const vespalib::string enum_store;
    static const vespalib::string multi_value;
    static const vespalib::string tensor_store;
    static const vespalib::string shared_string_repo;
    static const vespalib::string hnsw_levels_store;
    static const vespalib::string hnsw_links_store;
    static const vespalib::string hnsw_nodeid_mapping;
};

}
