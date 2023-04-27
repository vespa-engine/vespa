// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "distance_function_factory.h"
#include <vespa/eval/eval/typed_cells.h>
#include <mutex>
#include <memory>

namespace search::tensor {

class MaximumSquaredNormStore {
private:
    std::mutex _lock;
    double _max_sq_norm;
public:
    MaximumSquaredNormStore() noexcept : _lock(), _max_sq_norm(0.0) {}
    double get_max(double value = 0.0) {
        std::lock_guard<std::mutex> guard(_lock);
        if (value > _max_sq_norm) [[unlikely]] {
            _max_sq_norm = value;
        }
        return _max_sq_norm;
    }
};

template<typename FloatType>
class MipsDistanceFunctionFactory : public DistanceFunctionFactory {
    std::shared_ptr<MaximumSquaredNormStore> _sq_norm_store;
public:
    MipsDistanceFunctionFactory() : _sq_norm_store(std::make_shared<MaximumSquaredNormStore>()) {}

    BoundDistanceFunction::UP for_query_vector(const vespalib::eval::TypedCells& lhs) override;

    BoundDistanceFunction::UP for_insertion_vector(const vespalib::eval::TypedCells& lhs) override;
};

}
