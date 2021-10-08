// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querynodes.h"
#include "termdatafromnode.h"
#include "viewresolver.h"
#include "handlerecorder.h"
#include <vespa/searchlib/query/tree/templatetermvisitor.h>
#include <vespa/searchlib/queryeval/orsearch.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.querynodes");

using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::IIndexEnvironment;
using search::fef::MatchData;
using search::fef::MatchDataDetails;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::query::Node;
using search::query::TemplateTermVisitor;
using search::query::Weight;
using search::queryeval::OrSearch;
using search::queryeval::SearchIterator;
using std::map;
using std::vector;
using vespalib::string;

namespace proton::matching {

ProtonTermData::ProtonTermData() = default;
ProtonTermData::ProtonTermData(const ProtonTermData &) = default;
ProtonTermData & ProtonTermData::operator = (const ProtonTermData &) = default;
ProtonTermData::~ProtonTermData() = default;

void
ProtonTermData::propagate_document_frequency(uint32_t matching_doc_count, uint32_t total_doc_count)
{
    for (size_t i = 0; i < _fields.size(); ++i) {
        _fields[i].setDocFreq(matching_doc_count, total_doc_count);
    }
}

void
ProtonTermData::resolve(const ViewResolver &resolver,
                        const IIndexEnvironment &idxEnv,
                        const string &view,
                        bool forceFilter)
{
    std::vector<string> fields;
    resolver.resolve(((view == "") ? "default" : view), fields);
    _fields.clear();
    _fields.reserve(fields.size());
    for (size_t i = 0; i < fields.size(); ++i) {
        const FieldInfo *info = idxEnv.getFieldByName(fields[i]);
        if (info != 0) {
            _fields.emplace_back(fields[i], info->id());
            _fields.back().attribute_field =
                (info->type() == FieldType::ATTRIBUTE) ||
                (info->type() == FieldType::HIDDEN_ATTRIBUTE);
            _fields.back().filter_field = forceFilter ? true : info->isFilter();
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
        if (child == 0) {
            LOG(warning, "child of equiv is not a term");
            continue;
        }
        for (size_t j = 0; j < child->numFields(); ++j) {
            FieldSpec subSpec = child->field(j).fieldSpec();
            if (lookupField(subSpec.getFieldId()) == 0) {
                // this must happen before handles are reserved
                LOG_ASSERT(subSpec.getHandle() == search::fef::IllegalHandle);
                _fields.emplace_back(subSpec.getName(), subSpec.getFieldId());
            }
        }
    }
}

void
ProtonTermData::allocateTerms(MatchDataLayout &mdl)
{
    for (size_t i = 0; i < _fields.size(); ++i) {
        _fields[i].setHandle(mdl.allocTermField(_fields[i].getFieldId()));
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
    TermFieldHandle handle(search::fef::SimpleTermFieldData::getHandle(requested_details));
    HandleRecorder::register_handle(handle, requested_details);
    return handle;
}


}
