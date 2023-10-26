// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/idestructorcallback.h>
#include <future>

namespace proton {

/*
 * This token is shared between flushes initiated from a priority flush
 * strategy (cf. Proton::triggerFLush and Proton::prepareRestart).
 */
class PriorityFlushToken : public vespalib::IDestructorCallback {
    std::promise<void> _promise;
public:
    PriorityFlushToken(std::promise<void> promise);
    ~PriorityFlushToken() override;
};

}
