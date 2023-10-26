// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib { class Stash; }

namespace vespalib::slime {

class Value;

/**
 * Interface used to create a value object with type and content known
 * by the implementation, but not by the caller.
 **/
struct ValueFactory {
    virtual Value *create(Stash & stash) const = 0;
    virtual ~ValueFactory() = default;
};

} // namespace vespalib::slime
