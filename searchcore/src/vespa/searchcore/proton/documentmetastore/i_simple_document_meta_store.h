// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bucket_handler.h"
#include "i_store.h"

namespace proton {

/**
 * Interface for a simple document meta store that combines the
 * interfaces IStore and IBucketHandler.
 */
struct ISimpleDocumentMetaStore : public documentmetastore::IStore,
                                  public documentmetastore::IBucketHandler
{
    ~ISimpleDocumentMetaStore() override = default;
};

} // namespace proton

