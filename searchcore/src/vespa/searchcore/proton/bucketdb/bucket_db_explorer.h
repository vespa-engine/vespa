// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_db_owner.h"
#include <vespa/vespalib/net/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of a bucket db and its buckets.
 */
class BucketDBExplorer : public vespalib::StateExplorer
{
private:
    BucketDBOwner::Guard _bucketDb;

public:
    BucketDBExplorer(BucketDBOwner::Guard bucketDb);
    ~BucketDBExplorer();

    // Implements vespalib::StateExplorer
    virtual void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton
