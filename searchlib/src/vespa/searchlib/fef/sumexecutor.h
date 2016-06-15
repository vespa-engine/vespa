// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "featureexecutor.h"

namespace search {
namespace fef {

class MatchData;

/**
 * Simple executor that calculates the sum of a set of inputs. This
 * will be moved to another library as it is not really part of the
 * framework.
 **/
class SumExecutor : public FeatureExecutor
{
public:
    virtual void execute(MatchData &data);

    /**
     * Create an instance of this class and return it as a shared pointer.
     *
     * @return shared pointer to new instance
     **/
    static FeatureExecutor::LP create() { return FeatureExecutor::LP(new SumExecutor()); }
};

} // namespace fef
} // namespace search

