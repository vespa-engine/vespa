// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/gbdt.h>
#include "llvm_wrapper.h"

namespace vespalib {
namespace eval {
namespace gbdt {

/**
 * GBDT forest optimizer performing automatic function de-inlining.
 **/
class DeinlineForest : public Forest
{
private:
    using array_function = double (*)(const double *);

    LLVMWrapper                 _llvm_wrapper;
    std::vector<array_function> _fragments;

public:
    explicit DeinlineForest(const std::vector<const nodes::Node *> &trees);
    static Optimize::Result optimize(const ForestStats &stats,
                                     const std::vector<const nodes::Node *> &trees);
    static double eval(const Forest *forest, const double *input);
    static Optimize::Chain optimize_chain;
};

} // namespace vespalib::eval::gbdt
} // namespace vespalib::eval
} // namespace vespalib

