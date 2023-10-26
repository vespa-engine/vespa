// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "gbdt.h"

namespace vespalib {
namespace eval {
namespace gbdt {

/**
 * GBDT forest optimizer using a compact tree representation combined
 * with a leaf-node search and aggregate evaluation strategy. This
 * code is very similar to the old VM instruction for MLR expressions.
 **/
class VMForest : public Forest
{
private:
    std::vector<uint32_t> _model;

public:
    VMForest(std::vector<uint32_t> &&model) : _model(std::move(model)) {}
    static Optimize::Result less_only_optimize(const ForestStats &stats,
                                               const std::vector<const nodes::Node *> &trees);
    static double less_only_eval(const Forest *forest, const double *);
    static Optimize::Result general_optimize(const ForestStats &stats,
                                             const std::vector<const nodes::Node *> &trees);
    static double general_eval(const Forest *forest, const double *);
    static Optimize::Chain optimize_chain;
};

} // namespace vespalib::eval::gbdt
} // namespace vespalib::eval
} // namespace vespalib

