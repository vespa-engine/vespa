// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include <vespa/searchlib/attribute/attributesaver.h>

namespace search { class BufferWriter; }

namespace search::tensor {

class TensorStore;
class NearestNeighborIndexSaver;

/**
 * Class for saving a tensor attribute.
 * Will also save the nearest neighbor index if existing.
 */
class TensorAttributeSaver : public AttributeSaver {
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    using GenerationHandler = vespalib::GenerationHandler;
    using IndexSaverUP = std::unique_ptr<NearestNeighborIndexSaver>;

    RefCopyVector      _refs;
    const TensorStore& _tensor_store;
    IndexSaverUP _index_saver;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
    void save_dense_tensor_store(BufferWriter& writer, const DenseTensorStore& dense_tensor_store) const;
    void save_tensor_store(BufferWriter& writer) const;

public:
    TensorAttributeSaver(GenerationHandler::Guard &&guard,
                              const attribute::AttributeHeader &header,
                              RefCopyVector &&refs,
                              const TensorStore &tensor_store,
                              IndexSaverUP index_saver);

    ~TensorAttributeSaver() override;

    static vespalib::string index_file_suffix();
};

}
