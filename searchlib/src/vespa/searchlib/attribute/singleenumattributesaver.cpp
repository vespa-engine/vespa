// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleenumattributesaver.h"
#include "iattributesavetarget.h"
#include <vespa/searchlib/util/bufferwriter.h>

using search::attribute::EntryRefVector;
using vespalib::GenerationHandler;

namespace search {

SingleValueEnumAttributeSaver::
SingleValueEnumAttributeSaver(GenerationHandler::Guard &&guard,
                              const attribute::AttributeHeader &header,
                              EntryRefVector &&indices,
                              IEnumStore &enumStore)
    : AttributeSaver(std::move(guard), header),
      _indices(std::move(indices)),
      _enumSaver(enumStore)
{
}

SingleValueEnumAttributeSaver::~SingleValueEnumAttributeSaver() = default;

bool
SingleValueEnumAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    _enumSaver.writeUdat(saveTarget);
    std::unique_ptr<search::BufferWriter> datWriter(saveTarget.datWriter().allocBufferWriter());
    assert(saveTarget.getEnumerated());
    auto &enumerator = _enumSaver.get_enumerator();
    enumerator.enumerateValues();
    for (auto ref : _indices) {
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

}  // namespace search
