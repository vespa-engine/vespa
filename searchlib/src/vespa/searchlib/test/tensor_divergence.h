// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::eval {
struct Value;
}

namespace search::test {

/*
 * Computes the normalized root mean squared error (NRMSE) between the tensors `expected`
 * and `actual`, which are expected to have the same type. The error is computed across all
 * subspaces.
 *
 * NRMSE isn't necessarily the most intuitive error metric, but unlike other metrics (MAPE,
 * SMAPE etc.) it is well-defined for values of (or close to) zero, and it compensates for
 * different vector norms. The latter is very useful since vector dequantization precision
* is directly affected by the norm due to the scaling factor used during reconstruction.
 *
 * Throws vespalib::IllegalArgumentException if the tensor types of `expected` and `actual`
 * are different.
 */
[[nodiscard]] double compute_tensor_nrmse(const vespalib::eval::Value& expected, const vespalib::eval::Value& actual);

} // namespace search::test
