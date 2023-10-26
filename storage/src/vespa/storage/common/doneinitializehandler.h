// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::DoneInitializeHandler
 *
 * \brief Interface for handler needing to know when initializing is done.
 *
 * Every type of node will have one component responsible for calling this
 * handler.
 */

#pragma once

namespace storage {

struct DoneInitializeHandler {
    virtual ~DoneInitializeHandler() = default;
    virtual void notifyDoneInitializing() = 0;
};

} // storage

