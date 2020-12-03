// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_saver.h"
#include "direct_tensor_store.h"

#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::GenerationHandler;

namespace search::tensor {

DirectTensorAttributeSaver::
DirectTensorAttributeSaver(GenerationHandler::Guard &&guard,
                            const attribute::AttributeHeader &header,
                            RefCopyVector &&refs,
                            const DirectTensorStore &tensorStore)
    : AttributeSaver(std::move(guard), header),
      _refs(std::move(refs)),
      _tensorStore(tensorStore)
{
}


DirectTensorAttributeSaver::~DirectTensorAttributeSaver()
{
}

bool
DirectTensorAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    auto datWriter = saveTarget.datWriter().allocBufferWriter();
    const uint32_t docIdLimit(_refs.size());
    vespalib::nbostream stream;
    for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
        const vespalib::eval::Value *tensor = _tensorStore.get_tensor(_refs[lid]);
        if (tensor) {
            stream.clear();
            encode_value(*tensor, stream);
            uint32_t sz = stream.size();
            datWriter->write(&sz, sizeof(sz));
            datWriter->write(stream.peek(), stream.size());
        } else {
            uint32_t sz = 0;
            datWriter->write(&sz, sizeof(sz));
        }
    }
    datWriter->flush();
    return true;
}

}  // namespace search::tensor
