// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_attribute_saver.h"
#include "raw_buffer_store.h"
#include "raw_buffer_store_writer.h"
#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/util/bufferwriter.h>

namespace search::attribute {

ArrayBoolAttributeSaver::ArrayBoolAttributeSaver(vespalib::GenerationHandler::Guard&& guard,
                                                 const attribute::AttributeHeader& header,
                                                 EntryRefVector&& ref_vector,
                                                 const RawBufferStore& raw_store)
    : AttributeSaver(std::move(guard), header),
      _ref_vector(std::move(ref_vector)),
      _raw_store(raw_store)
{
}

ArrayBoolAttributeSaver::~ArrayBoolAttributeSaver() = default;

void
ArrayBoolAttributeSaver::save_raw_store(BufferWriter& writer) const
{
    RawBufferStoreWriter raw_writer(_raw_store, writer);
    for (auto ref : _ref_vector) {
        raw_writer.write(ref);
    }
    writer.flush();
}

bool
ArrayBoolAttributeSaver::onSave(IAttributeSaveTarget& saveTarget)
{
    assert(!saveTarget.getEnumerated());
    // Write .dat file: packed blobs per document (via RawBufferStoreWriter)
    std::unique_ptr<search::BufferWriter> dat_writer(saveTarget.datWriter().allocBufferWriter());
    save_raw_store(*dat_writer);
    // Write minimal .idx file (ReaderBase requires it for multi-value attributes)
    std::unique_ptr<search::BufferWriter> idx_writer(saveTarget.idxWriter().allocBufferWriter());
    uint32_t zero = 0;
    idx_writer->write(&zero, sizeof(uint32_t));
    idx_writer->flush();
    return true;
}

}
