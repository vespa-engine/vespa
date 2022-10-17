// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generation_hold_list.h"
#include "generationhandler.h"
#include <memory>

namespace vespalib {

class GenerationHeldBase
{
public:
    using generation_t = GenerationHandler::generation_t;
    using UP = std::unique_ptr<GenerationHeldBase>;
    using SP = std::shared_ptr<GenerationHeldBase>;

private:
    size_t	 _byte_size;

public:
    GenerationHeldBase(size_t byte_size_in)
        : _byte_size(byte_size_in)
    { }

    virtual ~GenerationHeldBase();
    size_t byte_size() const { return _byte_size; }
};

using GenerationHolderParent = GenerationHoldList<GenerationHeldBase::UP, true, false>;

/*
 * GenerationHolder is meant to hold large elements until readers can
 * no longer access them.
 */
class GenerationHolder : public GenerationHolderParent {
public:
    GenerationHolder();
};

}

