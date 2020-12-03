// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/typed_cells.h>

namespace vespalib::eval { class ValueType; struct Value; }
namespace vespalib::slime { struct Inserter; }

namespace search::tensor {

/**
 * Interface for tensor attribute used by feature executors to get information.
 */
class ITensorAttribute
{
public:
    virtual ~ITensorAttribute() {}
    virtual std::unique_ptr<vespalib::eval::Value> getTensor(uint32_t docId) const = 0;
    virtual std::unique_ptr<vespalib::eval::Value> getEmptyTensor() const = 0;
    virtual vespalib::eval::TypedCells extract_cells_ref(uint32_t docid) const = 0;
    virtual const vespalib::eval::Value& get_tensor_ref(uint32_t docid) const = 0;
    virtual bool supports_extract_cells_ref() const = 0;
    virtual bool supports_get_tensor_ref() const = 0;

    virtual const vespalib::eval::ValueType & getTensorType() const = 0;

    /**
     * Gets custom state for this tensor attribute by inserting it into the given Slime inserter.
     * This function is only called by the writer thread or when the writer thread is blocked.
     */
    virtual void get_state(const vespalib::slime::Inserter& inserter) const = 0;
};

}  // namespace search::tensor
