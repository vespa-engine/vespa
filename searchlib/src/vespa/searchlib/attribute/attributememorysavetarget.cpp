// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributememorysavetarget.h"
#include "attributefilesavetarget.h"
#include "attributevector.h"

namespace search {

using search::common::FileHeaderContext;

AttributeMemorySaveTarget::AttributeMemorySaveTarget()
    : _datWriter(),
      _idxWriter(),
      _weightWriter(),
      _udatWriter()
{
}

AttributeMemorySaveTarget::~AttributeMemorySaveTarget() {
}


IAttributeFileWriter &
AttributeMemorySaveTarget::datWriter()
{
    return _datWriter;
}


IAttributeFileWriter &
AttributeMemorySaveTarget::idxWriter()
{
    return _idxWriter;
}


IAttributeFileWriter &
AttributeMemorySaveTarget::weightWriter()
{
    return _weightWriter;
}


IAttributeFileWriter &
AttributeMemorySaveTarget::udatWriter()
{
    return _udatWriter;
}


bool
AttributeMemorySaveTarget::
writeToFile(const TuneFileAttributes &tuneFileAttributes,
            const FileHeaderContext &fileHeaderContext)
{
    AttributeFileSaveTarget saveTarget(tuneFileAttributes, fileHeaderContext);
    saveTarget.setHeader(_header);
    if (!saveTarget.setup()) {
        return false;
    }
    _datWriter.writeTo(saveTarget.datWriter());
    if (_header.getEnumerated()) {
        _udatWriter.writeTo(saveTarget.udatWriter());
    }
    if (_header.hasMultiValue()) {
        _idxWriter.writeTo(saveTarget.idxWriter());
        if (_header.hasWeightedSetType()) {
            _weightWriter.writeTo(saveTarget.weightWriter());
        }
    }
    saveTarget.close();
    return true;
}

} // namespace search

