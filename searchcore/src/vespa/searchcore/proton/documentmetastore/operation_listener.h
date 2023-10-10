// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace proton::documentmetastore {

/**
 * Interface used to listen to operations handled by the document meta store.
 */
class OperationListener {
public:
    using SP = std::shared_ptr<OperationListener>;
    virtual ~OperationListener() {}
    virtual void notify_remove_batch() = 0;
    virtual void notify_remove() = 0;
};

}
