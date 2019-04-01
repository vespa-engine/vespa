// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor.h"
#include "tensor_factory.h"
#include "tensor_builder.h"
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>

namespace vespalib::tensor {

std::unique_ptr<Tensor>
TensorFactory::create(const TensorCells &cells,
                      TensorBuilder &builder) {
    for (const auto &cell : cells) {
        for (const auto &addressElem : cell.first) {
            const auto &dimension = addressElem.first;
            builder.define_dimension(dimension);
        }
    }
    for (const auto &cell : cells) {
        for (const auto &addressElem : cell.first) {
            const auto &dimension = addressElem.first;
            const auto &label = addressElem.second;
            builder.add_label(builder.define_dimension(dimension), label);
        }
        builder.add_cell(cell.second);
    }
    return builder.build();
}


std::unique_ptr<Tensor>
TensorFactory::create(const TensorCells &cells,
                      const TensorDimensions &dimensions,
                      TensorBuilder &builder) {
    for (const auto &dimension : dimensions) {
        builder.define_dimension(dimension);
    }
    return create(cells, builder);
}


std::unique_ptr<Tensor>
TensorFactory::createDense(const DenseTensorCells &cells)
{
    std::map<std::string, size_t> dimensionSizes;
    DenseTensorBuilder builder;
    for (const auto &cell : cells) {
        for (const auto &addressElem : cell.first) {
            dimensionSizes[addressElem.first] =
                std::max(dimensionSizes[addressElem.first],
                         (addressElem.second + 1));
        }
    }
    std::map<std::string,
        typename DenseTensorBuilder::Dimension> dimensionEnums;
    for (const auto &dimensionElem : dimensionSizes) {
        dimensionEnums[dimensionElem.first] =
            builder.defineDimension(dimensionElem.first,
                                    dimensionElem.second);
    }
    for (const auto &cell : cells) {
        for (const auto &addressElem : cell.first) {
            const auto &dimension = addressElem.first;
            size_t label = addressElem.second;
            builder.addLabel(dimensionEnums[dimension], label);
        }
        builder.addCell(cell.second);
    }
    return builder.build();
}


}
