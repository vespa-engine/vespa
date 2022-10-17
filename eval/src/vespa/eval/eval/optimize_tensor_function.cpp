// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "optimize_tensor_function.h"
#include "tensor_function.h"
#include "simple_value.h"

#include <vespa/eval/instruction/dense_dot_product_function.h>
#include <vespa/eval/instruction/sparse_dot_product_function.h>
#include <vespa/eval/instruction/sparse_112_dot_product.h>
#include <vespa/eval/instruction/mixed_112_dot_product.h>
#include <vespa/eval/instruction/sparse_merge_function.h>
#include <vespa/eval/instruction/sparse_singledim_lookup.h>
#include <vespa/eval/instruction/sparse_no_overlap_join_function.h>
#include <vespa/eval/instruction/sparse_full_overlap_join_function.h>
#include <vespa/eval/instruction/mixed_inner_product_function.h>
#include <vespa/eval/instruction/sum_max_dot_product_function.h>
#include <vespa/eval/instruction/best_similarity_function.h>
#include <vespa/eval/instruction/dense_xw_product_function.h>
#include <vespa/eval/instruction/dense_matmul_function.h>
#include <vespa/eval/instruction/dense_multi_matmul_function.h>
#include <vespa/eval/instruction/fast_rename_optimizer.h>
#include <vespa/eval/instruction/add_trivial_dimension_optimizer.h>
#include <vespa/eval/instruction/dense_single_reduce_function.h>
#include <vespa/eval/instruction/remove_trivial_dimension_optimizer.h>
#include <vespa/eval/instruction/dense_lambda_peek_optimizer.h>
#include <vespa/eval/instruction/unpack_bits_function.h>
#include <vespa/eval/instruction/dense_simple_expand_function.h>
#include <vespa/eval/instruction/mixed_simple_join_function.h>
#include <vespa/eval/instruction/join_with_number_function.h>
#include <vespa/eval/instruction/pow_as_map_optimizer.h>
#include <vespa/eval/instruction/inplace_map_function.h>
#include <vespa/eval/instruction/vector_from_doubles_function.h>
#include <vespa/eval/instruction/dense_tensor_create_function.h>
#include <vespa/eval/instruction/dense_tensor_peek_function.h>
#include <vespa/eval/instruction/dense_hamming_distance.h>
#include <vespa/eval/instruction/l2_distance.h>
#include <vespa/eval/instruction/simple_join_count.h>
#include <vespa/eval/instruction/mapped_lookup.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.eval.optimize_tensor_function");

namespace vespalib::eval {

namespace {

using Child = TensorFunction::Child;

template <typename Func>
void run_optimize_pass(const Child &root, Func&& optimize_node) {
    std::vector<Child::CREF> nodes({root});
    for (size_t i = 0; i < nodes.size(); ++i) {
        nodes[i].get().get().push_children(nodes);
    }
    while (!nodes.empty()) {
        optimize_node(nodes.back().get());
        nodes.pop_back();
    }
}

const TensorFunction &optimize_for_factory(const ValueBuilderFactory &, const TensorFunction &expr, Stash &stash) {
    Child root(expr);
    run_optimize_pass(root, [&stash](const Child &child)
                      {
                          child.set(PowAsMapOptimizer::optimize(child.get(), stash));
                      });
    run_optimize_pass(root, [&stash](const Child &child)
                      {
                          child.set(SumMaxDotProductFunction::optimize(child.get(), stash));
                      });
    run_optimize_pass(root, [&stash](const Child &child)
                      {
                          child.set(Sparse112DotProduct::optimize(child.get(), stash));
                          child.set(Mixed112DotProduct::optimize(child.get(), stash));
                          child.set(BestSimilarityFunction::optimize(child.get(), stash));
                          child.set(L2Distance::optimize(child.get(), stash));                          
                      });
    run_optimize_pass(root, [&stash](const Child &child)
                      {
                          child.set(DenseDotProductFunction::optimize(child.get(), stash));
                          child.set(SparseDotProductFunction::optimize(child.get(), stash));
                          child.set(DenseXWProductFunction::optimize(child.get(), stash));
                          child.set(DenseMatMulFunction::optimize(child.get(), stash));
                          child.set(DenseMultiMatMulFunction::optimize(child.get(), stash));
                          child.set(MixedInnerProductFunction::optimize(child.get(), stash));
                          child.set(DenseHammingDistance::optimize(child.get(), stash));
                          child.set(SimpleJoinCount::optimize(child.get(), stash));
                          child.set(MappedLookup::optimize(child.get(), stash));
                      });
    run_optimize_pass(root, [&stash](const Child &child)
                      {
                          child.set(DenseSimpleExpandFunction::optimize(child.get(), stash));
                          child.set(AddTrivialDimensionOptimizer::optimize(child.get(), stash));
                          child.set(RemoveTrivialDimensionOptimizer::optimize(child.get(), stash));
                          child.set(VectorFromDoublesFunction::optimize(child.get(), stash));
                          child.set(DenseTensorCreateFunction::optimize(child.get(), stash));
                          child.set(DenseTensorPeekFunction::optimize(child.get(), stash));
                          child.set(DenseLambdaPeekOptimizer::optimize(child.get(), stash));
                          child.set(UnpackBitsFunction::optimize(child.get(), stash));
                          child.set(FastRenameOptimizer::optimize(child.get(), stash));
                          child.set(InplaceMapFunction::optimize(child.get(), stash));
                          child.set(MixedSimpleJoinFunction::optimize(child.get(), stash));
                          child.set(JoinWithNumberFunction::optimize(child.get(), stash));
                          child.set(DenseSingleReduceFunction::optimize(child.get(), stash));
                          child.set(SparseMergeFunction::optimize(child.get(), stash));
                          child.set(SparseNoOverlapJoinFunction::optimize(child.get(), stash));
                          child.set(SparseFullOverlapJoinFunction::optimize(child.get(), stash));
                          child.set(SparseSingledimLookup::optimize(child.get(), stash));
                      });
    return root.get();
}

} // namespace vespalib::eval::<unnamed>

const TensorFunction &optimize_tensor_function(const ValueBuilderFactory &factory, const TensorFunction &function, Stash &stash) {
    LOG(debug, "tensor function before optimization:\n%s\n", function.as_string().c_str());
    const TensorFunction &optimized = optimize_for_factory(factory, function, stash);
    LOG(debug, "tensor function after optimization:\n%s\n", optimized.as_string().c_str());
    return optimized;
}

} // namespace vespalib::eval
