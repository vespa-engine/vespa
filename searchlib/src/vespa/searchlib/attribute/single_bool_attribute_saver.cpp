// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_bool_attribute_saver.h"

#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/util/bufferwriter.h>

#include <cassert>

using vespalib::GenerationGuard;

namespace search::attribute {

SingleBoolAttributeSaver::SingleBoolAttributeSaver(const AttributeHeader&     header,
                                                   TransientBitVectorSnapshot bv_snapshot)
    : AttributeSaver(GenerationGuard(), header), _bv_snapshot(std::move(bv_snapshot)) {
}

SingleBoolAttributeSaver::~SingleBoolAttributeSaver() = default;

bool SingleBoolAttributeSaver::onSave(IAttributeSaveTarget& saveTarget) {
    std::unique_ptr<search::BufferWriter> writer(saveTarget.datWriter().allocBufferWriter());
    assert(!saveTarget.getEnumerated());
    const auto& bv = _bv_snapshot.bitvector();
    assert(bv.getStartIndex() == 0);
    uint32_t bits = bv.size();
    writer->write(&bits, sizeof(bits));
    auto entry_size = bv.legacy_num_bytes_with_single_guard_bit(bits);
    writer->write(bv.getStart(), entry_size);
    writer->flush();
    return true;
}

} // namespace search::attribute
