// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "generic_tensor_attribute_saver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include "generic_tensor_store.h"

using vespalib::GenerationHandler;
using search::IAttributeSaveTarget;

namespace search {

namespace attribute {

GenericTensorAttributeSaver::
GenericTensorAttributeSaver(GenerationHandler::Guard &&guard,
                            const IAttributeSaveTarget::Config &cfg,
                            RefCopyVector &&refs,
                            const GenericTensorStore &tensorStore)
    : AttributeSaver(std::move(guard), cfg),
      _refs(std::move(refs)),
      _tensorStore(tensorStore)
{
}


GenericTensorAttributeSaver::~GenericTensorAttributeSaver()
{
}


bool
GenericTensorAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
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


}  // namespace search::attribute

}  // namespace search
