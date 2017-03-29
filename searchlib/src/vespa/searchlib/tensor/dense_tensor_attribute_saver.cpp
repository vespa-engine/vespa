// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "dense_tensor_attribute_saver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include "dense_tensor_store.h"
#include <vespa/searchlib/attribute/iattributesavetarget.h>

using vespalib::GenerationHandler;

namespace search {

namespace tensor {

namespace {

static const uint8_t tensorIsNotPresent = 0;
static const uint8_t tensorIsPresent = 1;

}

DenseTensorAttributeSaver::
DenseTensorAttributeSaver(GenerationHandler::Guard &&guard,
                          const attribute::AttributeHeader &header,
                          RefCopyVector &&refs,
                          const DenseTensorStore &tensorStore)
    : AttributeSaver(std::move(guard), header),
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
    const uint32_t unboundDimSizesSize = _tensorStore.unboundDimSizesSize();
    const uint32_t docIdLimit(_refs.size());
    const uint32_t cellSize = _tensorStore.getCellSize();
    for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
        if (_refs[lid].valid()) {
            auto raw = _tensorStore.getRawBuffer(_refs[lid]);
            datWriter->write(&tensorIsPresent, sizeof(tensorIsPresent));
            size_t numCells = _tensorStore.getNumCells(raw);
            size_t rawLen = numCells * cellSize + unboundDimSizesSize;
            datWriter->write(static_cast<const char *>(raw) - unboundDimSizesSize,
                             rawLen);
        } else {
            datWriter->write(&tensorIsNotPresent, sizeof(tensorIsNotPresent));
        }
    }
    datWriter->flush();
    return true;
}


}  // namespace search::tensor

}  // namespace search
