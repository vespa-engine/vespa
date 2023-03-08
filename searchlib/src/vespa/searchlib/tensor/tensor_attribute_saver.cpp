// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute_saver.h"
#include "dense_tensor_store.h"
#include "nearest_neighbor_index_saver.h"
#include "tensor_attribute_constants.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <cassert>

using vespalib::GenerationHandler;

namespace search::tensor {

TensorAttributeSaver::TensorAttributeSaver(GenerationHandler::Guard &&guard,
                                           const attribute::AttributeHeader &header,
                                           attribute::EntryRefVector&& refs,
                                           const TensorStore &tensor_store,
                                           IndexSaverUP index_saver)
    : AttributeSaver(std::move(guard), header),
      _refs(std::move(refs)),
      _tensor_store(tensor_store),
      _index_saver(std::move(index_saver))
{
}

TensorAttributeSaver::~TensorAttributeSaver() = default;

vespalib::string
TensorAttributeSaver::index_file_suffix()
{
    return "nnidx";
}

bool
TensorAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    if (_index_saver) {
        if (!saveTarget.setup_writer(index_file_suffix(), "Binary data file for nearest neighbor index")) {
            return false;
        }
    }

    auto dat_writer = saveTarget.datWriter().allocBufferWriter();
    auto dense_tensor_store = _tensor_store.as_dense();
    if (dense_tensor_store != nullptr) {
        save_dense_tensor_store(*dat_writer, *dense_tensor_store);
    } else {
        save_tensor_store(*dat_writer);
    }
    if (_index_saver) {
        auto index_writer = saveTarget.get_writer(index_file_suffix()).allocBufferWriter();
        // Note: Implementation of save() is responsible to call BufferWriter::flush().
        _index_saver->save(*index_writer);
    }
    return true;
}

void
TensorAttributeSaver::save_tensor_store(BufferWriter& writer) const
{
    assert(get_header_version() == TENSOR_ATTRIBUTE_VERSION);
    const uint32_t docid_limit(_refs.size());
    vespalib::nbostream stream;
    for (uint32_t lid = 0; lid < docid_limit; ++lid) {
        if (_tensor_store.encode_stored_tensor(_refs[lid], stream)) {
            uint32_t sz = stream.size();
            writer.write(&sz, sizeof(sz));
            writer.write(stream.peek(), stream.size());
            stream.clear();
        } else {
            uint32_t sz = 0;
            writer.write(&sz, sizeof(sz));
        }
    }
    writer.flush();
}

void
TensorAttributeSaver::save_dense_tensor_store(BufferWriter& writer, const DenseTensorStore& dense_tensor_store) const
{
    assert(get_header_version() == DENSE_TENSOR_ATTRIBUTE_VERSION);
    auto raw_size = dense_tensor_store.getBufSize();
    const uint32_t docid_limit(_refs.size());
    for (uint32_t lid = 0; lid < docid_limit; ++lid) {
        if (_refs[lid].valid()) {
            auto raw = dense_tensor_store.getRawBuffer(_refs[lid]);
            writer.write(&tensorIsPresent, sizeof(tensorIsPresent));
            writer.write(static_cast<const char *>(raw), raw_size);
        } else {
            writer.write(&tensorIsNotPresent, sizeof(tensorIsNotPresent));
        }
    }
    writer.flush();
}

}
