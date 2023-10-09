// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querynodes.h"
#include "termdatafromnode.h"
#include "viewresolver.h"
#include "handlerecorder.h"
#include <vespa/vespalib/util/issue.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.querynodes");

using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::IIndexEnvironment;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::query::Node;
using vespalib::string;
using vespalib::Issue;

namespace proton::matching {

ProtonTermData::ProtonTermData() noexcept = default;
ProtonTermData::~ProtonTermData() = default;

namespace {

bool
is_attribute(FieldType type) noexcept {
    return (type == FieldType::ATTRIBUTE) || (type == FieldType::HIDDEN_ATTRIBUTE);
}

}

void
ProtonTermData::propagate_document_frequency(uint32_t matching_doc_count, uint32_t total_doc_count)
{
    for (size_t i = 0; i < _fields.size(); ++i) {
        _fields[i].setDocFreq(matching_doc_count, total_doc_count);
    }
}

void
ProtonTermData::resolve(const ViewResolver &resolver, const IIndexEnvironment &idxEnv,
                        const string &view, bool forceFilter)
{
    std::vector<string> fields;
    resolver.resolve(((view == "") ? "default" : view), fields);
    _fields.clear();
    _fields.reserve(fields.size());
    for (size_t i = 0; i < fields.size(); ++i) {
        const FieldInfo *info = idxEnv.getFieldByName(fields[i]);
        if (info != nullptr) {
            _fields.emplace_back(fields[i], info->id(), forceFilter || info->isFilter());
            FieldEntry & field = _fields.back();
            field.attribute_field = is_attribute(info->type());
        } else {
            LOG(debug, "ignoring undefined field: '%s'", fields[i].c_str());
        }
    }
}

void
ProtonTermData::resolveFromChildren(const std::vector<Node *> &subterms)
{
    for (size_t i = 0; i < subterms.size(); ++i) {
        const ProtonTermData *child = termDataFromNode(*subterms[i]);
        if (child == nullptr) {
            Issue::report("child of equiv is not a term");
            continue;
        }
        for (size_t j = 0; j < child->numFields(); ++j) {
            const FieldEntry & subSpec = child->field(j);
            if (lookupField(subSpec.getFieldId()) == nullptr) {
                // this must happen before handles are reserved
                LOG_ASSERT(subSpec.getHandle() == search::fef::IllegalHandle);
                _fields.emplace_back(subSpec._field_spec.getName(), subSpec.getFieldId(), false);
            }
        }
    }
}

void
ProtonTermData::allocateTerms(MatchDataLayout &mdl)
{
    for (size_t i = 0; i < _fields.size(); ++i) {
        _fields[i]._field_spec.setHandle(mdl.allocTermField(_fields[i].getFieldId()));
    }
}

void
ProtonTermData::setDocumentFrequency(uint32_t estHits, uint32_t docIdLimit)
{
    if (docIdLimit > 1) {
        propagate_document_frequency(estHits, docIdLimit - 1);
    } else {
        propagate_document_frequency(0, 1);
    }
}

const ProtonTermData::FieldEntry *
ProtonTermData::lookupField(uint32_t fieldId) const
{
    for (size_t i = 0; i < _fields.size(); ++i) {
        if (_fields[i].getFieldId() == fieldId) {
            return &_fields[i];
        }
    }
    return 0;
}

TermFieldHandle
ProtonTermData::FieldEntry::getHandle(MatchDataDetails requested_details) const
{
    TermFieldHandle handle(_field_spec.getHandle());
    HandleRecorder::register_handle(handle, requested_details);
    return handle;
}


}
