// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/exceptions.h>

using vespalib::IllegalArgumentException;
using vespalib::eval::Aggr;
using vespalib::eval::ReferenceOperations;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;

namespace search::test {

double compute_tensor_nrmse(const vespalib::eval::Value& expected, const vespalib::eval::Value& actual) {
    const auto exp_spec = TensorSpec::from_value(expected);
    const auto arg_spec = TensorSpec::from_value(actual);
    if (exp_spec.type() != arg_spec.type()) [[unlikely]] {
        throw IllegalArgumentException("Can't compute NRMSE between tensors; types do not match");
    }
    // Root mean squared error (RMSE)
    // For simplicity, compute across all subspaces. Quantization works on a per
    // dense subspace basis, so we may want to change this to be subspace-aware.
    // We also casually assume tensors have at least 1 dimension.
    const double n = ReferenceOperations::reduce(exp_spec, Aggr::COUNT, {}).as_double();
    auto         err_sq = ReferenceOperations::merge(exp_spec, arg_spec, [](double a, double b) noexcept {
        const double diff = a - b;
        return diff * diff;
    });
    const double sum_err_sq = ReferenceOperations::reduce(err_sq, Aggr::SUM, {}).as_double();
    const double mse = sum_err_sq / n;
    const double rmse = std::sqrt(mse);
    // Normalized root mean squared error (NRSME)
    // We choose to normalize based on the max observed cell value in the expected tensor
    // Ref: https://en.wikipedia.org/wiki/Root_mean_square_deviation#Normalization
    const double exp_max = ReferenceOperations::reduce(exp_spec, Aggr::MAX, {}).as_double();
    const double nrmse = (exp_max != 0) ? (rmse / exp_max) : rmse;
    return nrmse;
}

} // namespace search::test
