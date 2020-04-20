// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include <vespa/searchlib/attribute/attributesaver.h>

namespace search { class BufferWriter; }

namespace search::tensor {

class DenseTensorStore;
class NearestNeighborIndexSaver;

/**
 * Class for saving a dense tensor attribute.
 * Will also save the nearest neighbor index if existing.
 */
class DenseTensorAttributeSaver : public AttributeSaver {
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    using GenerationHandler = vespalib::GenerationHandler;
    using IndexSaverUP = std::unique_ptr<NearestNeighborIndexSaver>;

    RefCopyVector      _refs;
    const DenseTensorStore &_tensorStore;
    IndexSaverUP _index_saver;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
    void save_tensor_store(BufferWriter& writer) const;

public:
    DenseTensorAttributeSaver(GenerationHandler::Guard &&guard,
                              const attribute::AttributeHeader &header,
                              RefCopyVector &&refs,
                              const DenseTensorStore &tensorStore,
                              IndexSaverUP index_saver);

    ~DenseTensorAttributeSaver() override;

    static vespalib::string index_file_suffix();
};

}
