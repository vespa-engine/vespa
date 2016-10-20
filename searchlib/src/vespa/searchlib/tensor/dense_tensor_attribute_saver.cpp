// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_attribute_saver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include "dense_tensor_store.h"

using vespalib::GenerationHandler;
using search::IAttributeSaveTarget;

namespace search {

namespace attribute {

namespace {

static const uint8_t notPresent = 0;
static const uint32_t notPresent2 = 0;
static const uint8_t present = 1;

}

DenseTensorAttributeSaver::
DenseTensorAttributeSaver(GenerationHandler::Guard &&guard,
                     const IAttributeSaveTarget::Config &cfg,
                     RefCopyVector &&refs,
                     const DenseTensorStore &tensorStore)
    : AttributeSaver(std::move(guard), cfg),
      _refs(std::move(refs)),
      _tensorStore(tensorStore)
{
}


DenseTensorAttributeSaver::~DenseTensorAttributeSaver()
{
}


bool
DenseTensorAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    std::unique_ptr<BufferWriter>
        datWriter(saveTarget.datWriter().allocBufferWriter());
    const uint32_t dimSizeInfoSize = _tensorStore.dimSizeInfoSize();
    const uint32_t docIdLimit(_refs.size());
    const uint32_t unboundDims = _tensorStore.unboundDims();
    const uint32_t cellSize = _tensorStore.getCellSize();
    for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
        auto raw = _tensorStore.getRawBuffer(_refs[lid]);
        if (raw) {
            if (unboundDims == 0u) {
                datWriter->write(&present, sizeof(present));
            }
            size_t numCells = _tensorStore.getNumCells(raw);
            size_t rawLen = numCells * cellSize + dimSizeInfoSize;
            datWriter->write(static_cast<const char *>(raw) - dimSizeInfoSize,
                             rawLen);
        } else {
            if (unboundDims == 0u) {
                datWriter->write(&notPresent, sizeof(notPresent));
            } else {
                datWriter->write(&notPresent2, sizeof(notPresent2));
            }
        }
    }
    datWriter->flush();
    return true;
}


}  // namespace search::attribute

}  // namespace search
