// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdatabuilder.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".fef.matchdatabuilder");

namespace search::fef::test {

MatchDataBuilder::MatchDataBuilder(QueryEnvironment &queryEnv, MatchData &data) :
    _queryEnv(queryEnv),
    _data(data),
    _index(),
    _match()
{
    // reset all match data objects.
    for (TermFieldHandle handle = 0; handle < _data.getNumTermFields(); ++handle) {
        _data.resolveTermField(handle)->reset(TermFieldMatchData::invalidId());
    }
}

MatchDataBuilder::~MatchDataBuilder() {}

TermFieldMatchData *
MatchDataBuilder::getTermFieldMatchData(uint32_t termId, uint32_t fieldId)
{
    const ITermData *term = _queryEnv.getTerm(termId);
    if (term == nullptr) {
        return nullptr;
    }
    const ITermFieldData *field = term->lookupField(fieldId);
    if (field == nullptr || field->getHandle() >= _data.getNumTermFields()) {
        return nullptr;
    }
    return _data.resolveTermField(field->getHandle());
}


bool
MatchDataBuilder::setFieldLength(const vespalib::string &fieldName, uint32_t length)
{
    const FieldInfo *info = _queryEnv.getIndexEnv()->getFieldByName(fieldName);
    if (info == nullptr) {
        LOG(error, "Field '%s' does not exist.", fieldName.c_str());
        return false;
    }
    _index[info->id()].fieldLength = length;
    return true;
}

bool
MatchDataBuilder::addElement(const vespalib::string &fieldName, int32_t weight, uint32_t length)
{
    const FieldInfo *info = _queryEnv.getIndexEnv()->getFieldByName(fieldName);
    if (info == nullptr) {
        LOG(error, "Field '%s' does not exist.", fieldName.c_str());
        return false;
    }
    _index[info->id()].elements.push_back(MyElement(weight, length));
    return true;
}

bool
MatchDataBuilder::addOccurence(const vespalib::string &fieldName, uint32_t termId, uint32_t pos, uint32_t element)
{
    const FieldInfo *info = _queryEnv.getIndexEnv()->getFieldByName(fieldName);
    if (info == nullptr) {
        LOG(error, "Field '%s' does not exist.", fieldName.c_str());
        return false;
    }
    if (termId >= _queryEnv.getNumTerms()) {
        LOG(error, "Term id '%u' is invalid.", termId);
        return false;
    }
    const ITermFieldData *tfd = _queryEnv.getTerm(termId)->lookupField(info->id());
    if (tfd == nullptr) {
        LOG(error, "Field '%s' is not searched by the given term.",
            fieldName.c_str());
        return false;
    }
    _match[termId][info->id()].insert(Position(pos, element));
    return true;
}

bool
MatchDataBuilder::setWeight(const vespalib::string &fieldName, uint32_t termId, int32_t weight)
{
    const FieldInfo *info = _queryEnv.getIndexEnv()->getFieldByName(fieldName);
    if (info == nullptr) {
        LOG(error, "Field '%s' does not exist.", fieldName.c_str());
        return false;
    }
    if (termId >= _queryEnv.getNumTerms()) {
        LOG(error, "Term id '%u' is invalid.", termId);
        return false;
    }
    const ITermFieldData *tfd = _queryEnv.getTerm(termId)->lookupField(info->id());
    if (tfd == nullptr) {
        LOG(error, "Field '%s' is not searched by the given term.",
            fieldName.c_str());
        return false;
    }
    uint32_t eid = _index[info->id()].elements.size();
    _match[termId][info->id()].clear();
    _match[termId][info->id()].insert(Position(0, eid));
    _index[info->id()].elements.push_back(MyElement(weight, 1));
    return true;
}

bool
MatchDataBuilder::apply(uint32_t docId)
{
    // For each term, do
    for (const auto& term_elem : _match) {
        uint32_t termId = term_elem.first;

        for (const auto& field_elem : term_elem.second) {
            uint32_t fieldId = field_elem.first;
            TermFieldMatchData *match = getTermFieldMatchData(termId, fieldId);

            // Make sure there is a corresponding term field match data object.
            if (match == nullptr) {
                LOG(error, "Term id '%u' is invalid.", termId);
                return false;
            }
            match->reset(docId);

            // find field data
            MyField field;
            auto idxItr = _index.find(fieldId);
            if (idxItr != _index.end()) {
                field = idxItr->second;
            }

            // For log, attempt to lookup field name.
            const FieldInfo *info = _queryEnv.getIndexEnv()->getField(fieldId);
            vespalib::string name = info != nullptr ? info->name() : vespalib::make_string("%d", fieldId).c_str();

            // For each occurence of that term, in that field, do
            for (const auto& occ : field_elem.second) {
                // Append a term match position to the term match data.
                match->appendPosition(TermFieldMatchDataPosition(
                                              occ.eid,
                                              occ.pos,
                                              field.getWeight(occ.eid),
                                              field.getLength(occ.eid)));
                LOG(debug,
                    "Added occurence of term '%u' in field '%s'"
                    " at position '%u'.",
                    termId, name.c_str(), occ.pos);
                if (occ.pos >= field.getLength(occ.eid)) {
                    LOG(warning,
                        "Added occurence of term '%u' in field '%s'"
                        " at position '%u' >= fieldLen '%u'.",
                        termId, name.c_str(), occ.pos, field.getLength(occ.eid));
                }
            }
        }
    }
    // Return ok.
    return true;
}

}
