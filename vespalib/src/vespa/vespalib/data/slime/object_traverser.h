// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "inspector.h"

namespace vespalib::slime {

class Symbol;

/**
 * Interface used when traversing all the fields of an object value
 * tagged with symbol id.
 **/
struct ObjectSymbolTraverser {
    virtual void field(const Symbol &symbol, const Inspector &inspector) = 0;
    virtual ~ObjectSymbolTraverser() {}
};

/**
 * Interface used when traversing all the fields of an object value
 * tagged with symbol name.
 **/
struct ObjectTraverser {
    virtual void field(const Memory &symbol, const Inspector &inspector) = 0;
    virtual ~ObjectTraverser() {}
};

} // namespace vespalib::slime
