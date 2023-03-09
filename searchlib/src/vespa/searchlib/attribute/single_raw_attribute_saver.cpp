// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_raw_attribute_saver.h"
#include "raw_buffer_store.h"
#include "raw_buffer_store_writer.h"
#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/util/bufferwriter.h>

namespace search::attribute {

SingleRawAttributeSaver::SingleRawAttributeSaver(vespalib::GenerationHandler::Guard &&guard,
                                                 const attribute::AttributeHeader &header,
                                                 EntryRefVector&& ref_vector,
                                                 const RawBufferStore& raw_store)
    : AttributeSaver(std::move(guard), header),
      _ref_vector(std::move(ref_vector)),
      _raw_store(raw_store)
{
}

SingleRawAttributeSaver::~SingleRawAttributeSaver() = default;

void
SingleRawAttributeSaver::save_raw_store(BufferWriter& writer) const
{
    RawBufferStoreWriter raw_writer(_raw_store, writer);
    for (auto ref : _ref_vector) {
        raw_writer.write(ref);
    }
    writer.flush();
}

bool
SingleRawAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    std::unique_ptr<search::BufferWriter> writer(saveTarget.datWriter().allocBufferWriter());
    assert(!saveTarget.getEnumerated());
    save_raw_store(*writer);
    return true;
}

}
