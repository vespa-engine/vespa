// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace feedbm {

class BmStorageLink;

/*
 * This context is initialized by BmStorageChainBuilder.
 */
struct BmStorageLinkContext
{
    BmStorageLink* bm_link;
    BmStorageLinkContext()
        : bm_link(nullptr)
    {
    }
    ~BmStorageLinkContext() = default;
};

}
