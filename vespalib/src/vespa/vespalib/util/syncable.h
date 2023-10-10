// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/**
 * An syncable is an interface that waits for something to complete.
 **/
class Syncable
{
public:
    /**
     * Synchronize with this executor. This function will block until
     * all previously accepted tasks have been executed. This function
     * uses the event barrier algorithm (tm).
     *
     * @return this object; for chaining
     **/
    virtual Syncable &sync() = 0;

    virtual ~Syncable() {}
};

} // namespace vespalib

