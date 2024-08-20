// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/address_space.h>
#include <string>

namespace search {

/**
 * A set of components in an attribute vector that use address space.
 */
class AddressSpaceComponents {
public:
    static vespalib::AddressSpace default_enum_store_usage();
    static vespalib::AddressSpace default_multi_value_usage();
    static const std::string enum_store;
    static const std::string multi_value;
    static const std::string raw_store;
    static const std::string tensor_store;
    static const std::string shared_string_repo;
    static const std::string hnsw_levels_store;
    static const std::string hnsw_links_store;
    static const std::string hnsw_nodeid_mapping;
};

}
