// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace search::bmcluster {

class BmStorageLink;

/*
 * This context is initialized by BmStorageChainBuilder.
 */
struct BmStorageLinkContext
{
    BmStorageLink* bm_link;
    BmStorageLinkContext() noexcept
        : bm_link(nullptr)
    {
    }
    ~BmStorageLinkContext() = default;
};

}
