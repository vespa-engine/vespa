// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_match.h"
#include "sparse_tensor_address_decoder.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <assert.h>

namespace vespalib::tensor {

template<typename LCT, typename RCT>
void
SparseTensorMatch<LCT,RCT>::fastMatch(const SparseTensorT<LCT> &lhs, const SparseTensorT<RCT> &rhs)
{
    const auto & lhs_map = lhs.index().get_map();
    const auto & rhs_map = rhs.index().get_map();
    _builder.reserve(lhs_map.size());
    const auto rhs_map_end = rhs_map.end();
    for (const auto & kv : lhs_map) {
        auto rhsItr = rhs_map.find(kv.first);
        if (rhsItr != rhs_map_end) {
            LCT a = lhs.get_value(kv.second);
            RCT b = rhs.get_value(rhsItr->second);
            _builder.insertCell(kv.first, a * b);
        }
    }
}

template<typename LCT, typename RCT>
SparseTensorMatch<LCT,RCT>::SparseTensorMatch(const SparseTensorT<LCT> &lhs,
                                              const SparseTensorT<RCT> &rhs,
                                              eval::ValueType res_type)
    : _builder(std::move(res_type))
{
    fastMatch(lhs, rhs);
}

template class SparseTensorMatch<float,float>;
template class SparseTensorMatch<float,double>;
template class SparseTensorMatch<double,float>;
template class SparseTensorMatch<double,double>;

}
