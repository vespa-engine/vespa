// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::tensor {

/**
 * Interface for a class used to keep the result of the prepare step of a two-phase operation.
 */
class PrepareResult {
public:
    virtual ~PrepareResult() {}
};

}
