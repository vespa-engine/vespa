// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_store_saver.h"
#include "tensor_store.h"

#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::GenerationHandler;

namespace search::tensor {

TensorStoreSaver::
TensorStoreSaver(GenerationHandler::Guard &&guard,
                 const attribute::AttributeHeader &header,
                 RefCopyVector &&refs,
                 const TensorStore &tensorStore)
  : AttributeSaver(std::move(guard), header),
    _refs(std::move(refs)),
    _tensorStore(tensorStore)
{
}

TensorStoreSaver::~TensorStoreSaver() = default;

bool
TensorStoreSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    auto datWriter = saveTarget.datWriter().allocBufferWriter();
    const uint32_t docIdLimit(_refs.size());
    vespalib::nbostream stream;
    for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
        if (_tensorStore.encode_stored_tensor(_refs[lid], stream)) {
            uint32_t sz = stream.size();
            datWriter->write(&sz, sizeof(sz));
            datWriter->write(stream.peek(), stream.size());
            stream.clear();
        } else {
            uint32_t sz = 0;
            datWriter->write(&sz, sizeof(sz));
        }
    }
    datWriter->flush();
    return true;
}

}  // namespace search::tensor
