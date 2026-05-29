// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleenumattributesaver.h"

#include "iattributesavetarget.h"

#include <vespa/searchlib/util/bufferwriter.h>

using search::attribute::EntryRefVectorSnapshot;
using vespalib::GenerationGuard;

namespace search {

SingleValueEnumAttributeSaver::SingleValueEnumAttributeSaver(GenerationGuard&&                 guard,
                                                             const attribute::AttributeHeader& header,
                                                             EntryRefVectorSnapshot&&          indices_snapshot,
                                                             IEnumStore&                       enumStore)
    : AttributeSaver(std::move(guard), header),
      _indices_snapshot(std::move(indices_snapshot)),
      _enumSaver(enumStore) {
}

SingleValueEnumAttributeSaver::~SingleValueEnumAttributeSaver() = default;

bool SingleValueEnumAttributeSaver::onSave(IAttributeSaveTarget& saveTarget) {
    _enumSaver.writeUdat(saveTarget);
    std::unique_ptr<search::BufferWriter> datWriter(saveTarget.datWriter().allocBufferWriter());
    assert(saveTarget.getEnumerated());
    auto& enumerator = _enumSaver.get_enumerator();
    enumerator.enumerateValues();
    auto indices_span = _indices_snapshot.span();
    for (auto ref : indices_span) {
        uint32_t enumValue = enumerator.mapEntryRefToEnumValue(ref);
        assert(enumValue != 0u);
        // Enumerator enumerates known entry refs (based on dictionary tree)
        // to values >= 1, but file format starts enumeration at 0.
        --enumValue;
        datWriter->write(&enumValue, sizeof(uint32_t));
    }
    datWriter->flush();
    _enumSaver.clear();
    return true;
}

} // namespace search
