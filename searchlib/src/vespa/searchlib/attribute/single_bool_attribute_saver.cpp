// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_bool_attribute_saver.h"
#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <cassert>

namespace search::attribute {

SingleBoolAttributeSaver::SingleBoolAttributeSaver(const AttributeHeader &header, std::unique_ptr<const BitVector> bv)
    : AttributeSaver(vespalib::GenerationHandler::Guard(), header),
      _bv(std::move(bv))
{
}

SingleBoolAttributeSaver::~SingleBoolAttributeSaver() = default;

bool
SingleBoolAttributeSaver::onSave(IAttributeSaveTarget& saveTarget)
{
    std::unique_ptr<search::BufferWriter> writer(saveTarget.datWriter().allocBufferWriter());
    assert(!saveTarget.getEnumerated());
    assert(_bv->getStartIndex() == 0);
    uint32_t bits = _bv->size();
    writer->write(&bits, sizeof(bits));
    auto entry_size = _bv->legacy_num_bytes_with_single_guard_bit(bits);
    writer->write(_bv->getStart(), entry_size);
    writer->flush();
    return true;
}

}
