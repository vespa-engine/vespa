// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/btree/btree.h>

namespace search::predicate {

using BTreeSet = vespalib::btree::BTree<uint32_t, vespalib::btree::BTreeNoLeafData>;
using ZeroConstraintDocs = BTreeSet::FrozenView;

struct Constants {
    static const vespalib::string z_star_attribute_name;
    static const uint64_t         z_star_hash;
    static const vespalib::string z_star_compressed_attribute_name;
    static const uint64_t         z_star_compressed_hash;
};

struct DocIdLimitProvider {
    virtual uint32_t getDocIdLimit() const = 0;
    virtual uint32_t getCommittedDocIdLimit() const = 0;
    virtual ~DocIdLimitProvider() {}
};

}
