// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_small_numeric_attribute_saver.h"
#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <cassert>

namespace search::attribute {

SingleSmallNumericAttributeSaver::SingleSmallNumericAttributeSaver(const attribute::AttributeHeader& header,
                                                                   uint32_t num_docs,
                                                                   std::vector<uint32_t> word_data)
    : AttributeSaver(vespalib::GenerationHandler::Guard(), header),
      _num_docs(num_docs),
      _word_data(std::move(word_data))
{
}

SingleSmallNumericAttributeSaver::~SingleSmallNumericAttributeSaver() = default;

bool
SingleSmallNumericAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    std::unique_ptr<search::BufferWriter> writer(saveTarget.datWriter().allocBufferWriter());
    assert(!saveTarget.getEnumerated());
    writer->write(&_num_docs, sizeof(_num_docs));
    if (!_word_data.empty()) {
        writer->write(_word_data.data(), sizeof(uint32_t) * _word_data.size());
    }
    writer->flush();
    return true;
}

}
