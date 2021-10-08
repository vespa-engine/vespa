// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "joinable.h"

namespace vespalib {

/**
 * Interface used to abstract entities that are the source of
 * activity.
 **/
struct Active : Joinable {
    /**
     * Start activity.
     **/
    virtual void start() = 0;

    /**
     * Request that activity stops. The returned object can be used to
     * wait for the actual conclusion of the activity.
     *
     * @return object that can be used to wait for activity completion
     **/
    virtual Joinable &stop() = 0;

    /**
     * Empty virtual destructor to enable subclassing.
     **/
    virtual ~Active() {}
};

} // namespace vespalib

