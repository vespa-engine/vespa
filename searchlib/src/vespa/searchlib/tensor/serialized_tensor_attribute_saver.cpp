// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialized_tensor_attribute_saver.h"
#include "serialized_tensor_store.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/searchlib/attribute/iattributesavetarget.h>

using vespalib::GenerationHandler;

namespace search::tensor {

SerializedTensorAttributeSaver::
SerializedTensorAttributeSaver(GenerationHandler::Guard &&guard,
                               const attribute::AttributeHeader &header,
                               RefCopyVector &&refs,
                               const SerializedTensorStore &tensorStore)
    : AttributeSaver(std::move(guard), header),
      _refs(std::move(refs)),
      _tensorStore(tensorStore)
{
}


SerializedTensorAttributeSaver::~SerializedTensorAttributeSaver()
{
}


bool
SerializedTensorAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    std::unique_ptr<BufferWriter>
        datWriter(saveTarget.datWriter().allocBufferWriter());
    const uint32_t docIdLimit(_refs.size());
    for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
        auto raw = _tensorStore.getRawBuffer(_refs[lid]);
        datWriter->write(&raw.second, sizeof(raw.second));
        if (raw.second != 0) {
            datWriter->write(raw.first, raw.second);
        }
    }
    datWriter->flush();
    return true;
}

}
