// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_db_owner.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of a bucket db and its buckets.
 */
class BucketDBExplorer : public vespalib::StateExplorer
{
private:
    bucketdb::Guard _bucketDb;

public:
    explicit BucketDBExplorer(bucketdb::Guard bucketDb);
    ~BucketDBExplorer() override;

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton
