// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_storage_chain_builder.h"
#include "bm_storage_link_context.h"
#include "bm_storage_link.h"

#include <vespa/log/log.h>
LOG_SETUP(".bm_storage_chain_builder");

namespace search::bmcluster {

BmStorageChainBuilder::BmStorageChainBuilder()
    : storage::StorageChainBuilder(),
      _context(std::make_shared<BmStorageLinkContext>())
{
}

BmStorageChainBuilder::~BmStorageChainBuilder() = default;

void
BmStorageChainBuilder::add(std::unique_ptr<storage::StorageLink> link)
{
    vespalib::string name = link->getName();
    storage::StorageChainBuilder::add(std::move(link));
    LOG(info, "Added storage link '%s'", name.c_str());
    if (name == "Communication manager") {
        auto my_link = std::make_unique<BmStorageLink>();
        LOG(info, "Adding extra storage link '%s'", my_link->getName().c_str());
        _context->bm_link = my_link.get();
        storage::StorageChainBuilder::add(std::move(my_link));
    }
}

}
