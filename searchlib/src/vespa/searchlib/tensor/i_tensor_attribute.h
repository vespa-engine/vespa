// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib::tensor {
class MutableDenseTensorView;
class Tensor;
}
namespace vespalib::eval { class ValueType; }
namespace vespalib::slime { struct Inserter; }

namespace search::tensor {

/**
 * Interface for tensor attribute used by feature executors to get information.
 */
class ITensorAttribute
{
public:
    using Tensor = vespalib::tensor::Tensor;

    virtual ~ITensorAttribute() {}
    virtual std::unique_ptr<Tensor> getTensor(uint32_t docId) const = 0;
    virtual std::unique_ptr<Tensor> getEmptyTensor() const = 0;
    virtual void getTensor(uint32_t docId, vespalib::tensor::MutableDenseTensorView &tensor) const = 0;
    virtual vespalib::eval::ValueType getTensorType() const = 0;

    /**
     * Gets custom state for this tensor attribute by inserting it into the given Slime inserter.
     * This function is only called by the writer thread or when the writer thread is blocked.
     */
    virtual void get_state(const vespalib::slime::Inserter& inserter) const = 0;
};

}  // namespace search::tensor
