// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "param_usage.h"
#include "function.h"
#include "node_traverser.h"
#include "basic_nodes.h"

namespace vespalib {
namespace eval {

using namespace nodes;

namespace {

//-----------------------------------------------------------------------------

struct CountUsage : NodeTraverser {
    double p;
    std::vector<double> result;
    CountUsage(size_t num_params) : p(1.0), result(num_params, 0.0) {}
    ~CountUsage() override;
    bool open(const Node &node) override {
        if (auto if_node = as<If>(node)) {
            double my_p = p;
            if_node->cond().traverse(*this);
            p = my_p * if_node->p_true();
            if_node->true_expr().traverse(*this);
            p = my_p * (1 - if_node->p_true());
            if_node->false_expr().traverse(*this);
            p = my_p;
            return false;
        }
        return true;
    }
    void close(const Node &node) override {
        if (auto symbol = as<Symbol>(node)) {
            result[symbol->id()] += p;
        }
    }
};

CountUsage::~CountUsage() = default;

//-----------------------------------------------------------------------------

struct CheckUsage : NodeTraverser {
    std::vector<double> result;
    CheckUsage(size_t num_params) : result(num_params, 0.0) {}
    ~CheckUsage() override;
    void merge(const std::vector<double> &true_result,
               const std::vector<double> &false_result,
               double p_true)
    {
        for (size_t i = 0; i < result.size(); ++i) {
            double p_mixed = (true_result[i] * p_true) + (false_result[i] * (1 - p_true));
            double p_not_used = (1 - result[i]) * (1 - p_mixed);
            result[i] = (1 - p_not_used);
        }
    }
    bool open(const Node &node) override {
        if (auto if_node = as<If>(node)) {
            if_node->cond().traverse(*this);
            CheckUsage check_true(result.size());
            if_node->true_expr().traverse(check_true);
            CheckUsage check_false(result.size());
            if_node->false_expr().traverse(check_false);
            merge(check_true.result, check_false.result, if_node->p_true());
            return false;
        }
        return true;
    }
    void close(const Node &node) override {
        if (auto symbol = as<Symbol>(node)) {
            result[symbol->id()] = 1.0;
        }
    }
};

CheckUsage::~CheckUsage() = default;

//-----------------------------------------------------------------------------

} // namespace vespalib::eval::<unnamed>

std::vector<double>
count_param_usage(const Function &function)
{
    CountUsage count_usage(function.num_params());
    function.root().traverse(count_usage);
    return count_usage.result;
}

std::vector<double>
check_param_usage(const Function &function)
{
    CheckUsage check_usage(function.num_params());
    function.root().traverse(check_usage);
    return check_usage.result;
}

} // namespace vespalib::eval
} // namespace vespalib
