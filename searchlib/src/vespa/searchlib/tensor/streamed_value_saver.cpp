// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "streamed_value_saver.h"
#include "streamed_value_store.h"

#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::GenerationHandler;

namespace search::tensor {

StreamedValueSaver::
StreamedValueSaver(GenerationHandler::Guard &&guard,
                   const attribute::AttributeHeader &header,
                   RefCopyVector &&refs,
                   const StreamedValueStore &tensorStore)
  : AttributeSaver(std::move(guard), header),
    _refs(std::move(refs)),
    _tensorStore(tensorStore)
{
}

StreamedValueSaver::~StreamedValueSaver() = default;

bool
StreamedValueSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    auto datWriter = saveTarget.datWriter().allocBufferWriter();
    const uint32_t docIdLimit(_refs.size());
    vespalib::nbostream stream;
    for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
        if (_tensorStore.encode_tensor(_refs[lid], stream)) {
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
