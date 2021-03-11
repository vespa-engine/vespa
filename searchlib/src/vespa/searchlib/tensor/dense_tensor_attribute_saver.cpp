// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute_saver.h"
#include "dense_tensor_store.h"
#include "nearest_neighbor_index_saver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/searchlib/attribute/iattributesavetarget.h>

using vespalib::GenerationHandler;

namespace search::tensor {

namespace {

constexpr uint8_t tensorIsNotPresent = 0;
constexpr uint8_t tensorIsPresent = 1;

}

DenseTensorAttributeSaver::
DenseTensorAttributeSaver(GenerationHandler::Guard &&guard,
                          const attribute::AttributeHeader &header,
                          RefCopyVector &&refs,
                          const DenseTensorStore &tensorStore,
                          IndexSaverUP index_saver)
    : AttributeSaver(std::move(guard), header),
      _refs(std::move(refs)),
      _tensorStore(tensorStore),
      _index_saver(std::move(index_saver))
{
}

DenseTensorAttributeSaver::~DenseTensorAttributeSaver() = default;

vespalib::string
DenseTensorAttributeSaver::index_file_suffix()
{
    return "nnidx";
}

bool
DenseTensorAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    if (_index_saver) {
        if (!saveTarget.setup_writer(index_file_suffix(), "Binary data file for nearest neighbor index")) {
            return false;
        }
    }

    auto dat_writer = saveTarget.datWriter().allocBufferWriter();
    save_tensor_store(*dat_writer);

    if (_index_saver) {
        auto index_writer = saveTarget.get_writer(index_file_suffix()).allocBufferWriter();
        // Note: Implementation of save() is responsible to call BufferWriter::flush().
        _index_saver->save(*index_writer);
    }
    return true;
}

void
DenseTensorAttributeSaver::save_tensor_store(BufferWriter& writer) const
{
    const uint32_t docIdLimit(_refs.size());
    for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
        if (_refs[lid].valid()) {
            auto raw = _tensorStore.getRawBuffer(_refs[lid]);
            writer.write(&tensorIsPresent, sizeof(tensorIsPresent));
            size_t rawLen = _tensorStore.getBufSize();
            writer.write(static_cast<const char *>(raw), rawLen);
        } else {
            writer.write(&tensorIsNotPresent, sizeof(tensorIsNotPresent));
        }
    }
    writer.flush();
}

}
