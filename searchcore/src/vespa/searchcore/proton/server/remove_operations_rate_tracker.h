// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/operation_rate_tracker.h>
#include <vespa/searchcore/proton/documentmetastore/operation_listener.h>

namespace proton {

/**
 * Class that tracks the rate of remove operations handled by the document meta store.
 *
 * For each operation we can tell if it is above or below a given rate threshold.
 */
class RemoveOperationsRateTracker : public documentmetastore::OperationListener {
private:
    OperationRateTracker _remove_batch_tracker;

public:
    RemoveOperationsRateTracker(double remove_batch_rate_threshold);

    void notify_remove_batch() override;

    bool remove_batch_above_threshold() const;

    // Should only be used for testing
    void reset_remove_batch_tracker();
};

}
