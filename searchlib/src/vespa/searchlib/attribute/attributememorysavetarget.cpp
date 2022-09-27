// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributememorysavetarget.h"
#include "attributefilesavetarget.h"
#include "attributevector.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search {

using search::common::FileHeaderContext;
using vespalib::IllegalArgumentException;

AttributeMemorySaveTarget::AttributeMemorySaveTarget()
    : _datWriter(),
      _idxWriter(),
      _weightWriter(),
      _udatWriter(),
      _writers()
{
}

AttributeMemorySaveTarget::~AttributeMemorySaveTarget() = default;

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
    for (const auto& entry : _writers) {
        if (!saveTarget.setup_writer(entry.first, entry.second.desc)) {
            return false;
        }
        auto& file_writer = saveTarget.get_writer(entry.first);
        entry.second.writer->writeTo(file_writer);
    }
    saveTarget.close();
    return true;
}

bool
AttributeMemorySaveTarget::setup_writer(const vespalib::string& file_suffix,
                                        const vespalib::string& desc)
{
    auto writer = std::make_unique<AttributeMemoryFileWriter>();
    auto itr = _writers.find(file_suffix);
    if (itr != _writers.end()) {
        return false;
    }
    _writers.insert(std::make_pair(file_suffix, WriterEntry(std::move(writer), desc)));
    return true;
}

IAttributeFileWriter&
AttributeMemorySaveTarget::get_writer(const vespalib::string& file_suffix)
{
    auto itr = _writers.find(file_suffix);
    if (itr == _writers.end()) {
        throw IllegalArgumentException("File writer with suffix '" + file_suffix + "' does not exist");
    }
    return *itr->second.writer;
}

} // namespace search

