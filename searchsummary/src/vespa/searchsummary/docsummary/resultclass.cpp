// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultclass.h"
#include "docsum_field_writer.h"
#include <vespa/vespalib/stllike/hashtable.hpp>

namespace search::docsummary {

ResultClass::ResultClass(const char *name)
    : _name(name),
      _entries(),
      _nameMap(),
      _dynInfo(),
      _omit_summary_features(false),
      _num_field_writer_states(0)
{ }


ResultClass::~ResultClass() = default;

int
ResultClass::getIndexFromName(const char* name) const
{
    auto found = _nameMap.find(name);
    return (found != _nameMap.end()) ? found->second : -1;
}

bool
ResultClass::addConfigEntry(const char *name, std::unique_ptr<DocsumFieldWriter> docsum_field_writer)
{
    if (_nameMap.find(name) != _nameMap.end()) {
        return false;
    }

    _nameMap[name] = _entries.size();
    ResConfigEntry e(name);
    if (docsum_field_writer) {
        docsum_field_writer->setIndex(_entries.size());
        bool generated = docsum_field_writer->isGenerated();
        _dynInfo.update_override_counts(generated);
        if (docsum_field_writer->setFieldWriterStateIndex(_num_field_writer_states)) {
            ++_num_field_writer_states;
        }
    }
    e.set_writer(std::move(docsum_field_writer));
    _entries.push_back(std::move(e));
    return true;
}

bool
ResultClass::addConfigEntry(const char *name)
{
    return addConfigEntry(name, {});
}

bool
ResultClass::all_fields_generated(const vespalib::hash_set<vespalib::string>& fields) const
{
    if (_dynInfo._generateCnt == getNumEntries()) {
        return true;
    }
    if (fields.empty()) {
        return false;
    }
    for (const auto& entry : _entries) {
        if (fields.contains(entry.name()) && !entry.is_generated()) {
            return false;
        }
    }
    return true;
}

}
