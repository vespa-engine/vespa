// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.fieldinfo");

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/handle.h>
#include <sstream>
#include "fieldinfofeature.h"
#include "valuefeature.h"
#include "utils.h"

namespace search {
namespace features {

IndexFieldInfoExecutor::IndexFieldInfoExecutor(feature_t type, feature_t isFilter,
                                               uint32_t field, uint32_t fieldHandle)
    : fef::FeatureExecutor(),
      _type(type),
      _isFilter(isFilter),
      _field(field),
      _fieldHandle(fieldHandle)
{
    // empty
}

void
IndexFieldInfoExecutor::execute(fef::MatchData &data)
{
    *data.resolveFeature(outputs()[0]) = _type;
    *data.resolveFeature(outputs()[1]) = _isFilter;
    *data.resolveFeature(outputs()[2]) = 1.0f; // searched
    fef::TermFieldMatchData *tfmd = data.resolveTermField(_fieldHandle);
    if (tfmd->getDocId() == data.getDocId()) {
        *data.resolveFeature(outputs()[3]) = 1.0f; // hit
    } else {
        *data.resolveFeature(outputs()[3]) = 0.0f; // no hit
    }
    fef::FieldPositionsIterator itr = tfmd->getIterator();
    *data.resolveFeature(outputs()[4]) = itr.getFieldLength();
    if (itr.valid()) {
        uint32_t first = itr.getPosition();
        uint32_t last  = 0;
        uint32_t cnt   = 0;
        for (; itr.valid(); itr.next()) {
            last = itr.getPosition();
            ++cnt;
        }
        *data.resolveFeature(outputs()[5]) = first;
        *data.resolveFeature(outputs()[6]) = last;
        *data.resolveFeature(outputs()[7]) = cnt;
    } else {
        *data.resolveFeature(outputs()[5]) = fef::FieldPositionsIterator::UNKNOWN_LENGTH; // first
        *data.resolveFeature(outputs()[6]) = fef::FieldPositionsIterator::UNKNOWN_LENGTH; // last
        *data.resolveFeature(outputs()[7]) = 0.0f;
    }
}

//-----------------------------------------------------------------------------

AttrFieldInfoExecutor::AttrFieldInfoExecutor(feature_t type, uint32_t fieldHandle) :
    FeatureExecutor(),
    _type(type),
    _fieldHandle(fieldHandle)
{
    // empty
}

void
AttrFieldInfoExecutor::execute(fef::MatchData &data)
{
    *data.resolveFeature(outputs()[0]) = _type;
    *data.resolveFeature(outputs()[1]) = 0.0; // not filter
    *data.resolveFeature(outputs()[2]) = 1.0f; // searched
    fef::TermFieldMatchData *tfmd = data.resolveTermField(_fieldHandle);
    if (tfmd->getDocId() == data.getDocId()) {
        *data.resolveFeature(outputs()[3]) = 1.0f; // hit
        *data.resolveFeature(outputs()[4]) = fef::FieldPositionsIterator::UNKNOWN_LENGTH; // len
        *data.resolveFeature(outputs()[5]) = 0.0f; // first
        *data.resolveFeature(outputs()[6]) = 0.0f; // last
        *data.resolveFeature(outputs()[7]) = 1.0f;
    } else {
        *data.resolveFeature(outputs()[3]) = 0.0f; // no hit
        *data.resolveFeature(outputs()[4]) = fef::FieldPositionsIterator::UNKNOWN_LENGTH; // len
        *data.resolveFeature(outputs()[5]) = fef::FieldPositionsIterator::UNKNOWN_LENGTH; // first
        *data.resolveFeature(outputs()[6]) = fef::FieldPositionsIterator::UNKNOWN_LENGTH; // last
        *data.resolveFeature(outputs()[7]) = 0.0f;
    }
}

//-----------------------------------------------------------------------------

FieldInfoBlueprint::FieldInfoBlueprint() :
    fef::Blueprint("fieldInfo"),
    _overview(false),
    _indexcnt(0.0f),
    _attrcnt(0.0f),
    _type(0.0f),
    _isFilter(0.0f),
    _fieldId(fef::IllegalFieldId)
{
    // empty
}

void
FieldInfoBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &indexEnv,
                                      fef::IDumpFeatureVisitor &visitor) const
{
    if (!indexEnv.getProperties().lookup(getBaseName(), "enable").get("").empty()) {
        fef::FeatureNameBuilder fnb;
        fnb.baseName(getBaseName());
        for (uint32_t i = 0; i < indexEnv.getNumFields(); ++i) {
            const fef::FieldInfo *fi = indexEnv.getField(i);
            fnb.clearParameters().parameter(fi->name());
            fnb.output("type");
            visitor.visitDumpFeature(fnb.buildName());
            fnb.output("filter");
            visitor.visitDumpFeature(fnb.buildName());
            fnb.output("search");
            visitor.visitDumpFeature(fnb.buildName());
            fnb.output("hit");
            visitor.visitDumpFeature(fnb.buildName());
            fnb.output("len");
            visitor.visitDumpFeature(fnb.buildName());
            fnb.output("first");
            visitor.visitDumpFeature(fnb.buildName());
            fnb.output("last");
            visitor.visitDumpFeature(fnb.buildName());
            fnb.output("cnt");
            visitor.visitDumpFeature(fnb.buildName());
        }
        fnb.clearParameters();
        fnb.output("indexCnt");
        visitor.visitDumpFeature(fnb.buildName());
        fnb.output("attrCnt");
        visitor.visitDumpFeature(fnb.buildName());
    }
}

bool
FieldInfoBlueprint::setup(const fef::IIndexEnvironment &indexEnv,
                          const fef::ParameterList &params)
{
    if (params.empty()) {
        _overview = true;
        for (uint32_t i = 0; i < indexEnv.getNumFields(); ++i) {
            if (indexEnv.getField(i)->type() == fef::FieldType::INDEX) {
                _indexcnt += 1.0;
            }
            if (indexEnv.getField(i)->type() == fef::FieldType::ATTRIBUTE) {
                _attrcnt += 1.0;
            }
        }
        describeOutput("indexCnt", "total number of fields of type index");
        describeOutput("attrCnt", "total number of fields of type attribute");
        return true;
    }
    if (params.size() == 1) {
        vespalib::string name = params[0].getValue();
        const fef::FieldInfo *fi = indexEnv.getFieldByName(name);
        if (fi != 0) {
            _fieldId = fi->id();
            if (fi->type() == fef::FieldType::INDEX) {
                indexEnv.hintFieldAccess(_fieldId);
                _type = 1.0;
            } else if (fi->type() == fef::FieldType::ATTRIBUTE) {
                _type = 2.0;
            }
            if (fi->isFilter()) {
                _isFilter = 1.0;
            } else {
                _isFilter = 0.0;
            }
        }
        describeOutput("type", "1.0 for INDEX, 2.0 for ATTRIBUTE, 0.0 for unknown (from index env)");
        describeOutput("filter", "1.0 if this is a filter, 0.0 otherwise (from index env)");
        describeOutput("search", "1.0 means first term searched this field, 0.0 means it did not");
        describeOutput("hit", "1.0 means first term got a hit in this field, 0.0 means it did not");
        describeOutput("len", "field length in number of words");
        describeOutput("first", "position of the first hit of the first term in this field");
        describeOutput("last", "position of the last hit of the first term in this field");
        describeOutput("cnt", "number of hits for the first term in this field");
        return true;
    }
    return false;
}

fef::FeatureExecutor &
FieldInfoBlueprint::createExecutor(const fef::IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    if (_overview) {
        std::vector<feature_t> values;
        values.push_back(_indexcnt);
        values.push_back(_attrcnt);
        return stash.create<ValueExecutor>(values);
    }
    uint32_t fieldHandle = util::getTermFieldHandle(queryEnv, 0, _fieldId);
    if (fieldHandle == fef::IllegalHandle) {
        std::vector<feature_t> values;
        values.push_back(_type);
        values.push_back(_isFilter);
        values.push_back(0.0f); // not searched
        values.push_back(0.0f); // no hit
        values.push_back(fef::FieldPositionsIterator::UNKNOWN_LENGTH); // default field length
        values.push_back(fef::FieldPositionsIterator::UNKNOWN_LENGTH); // default first pos
        values.push_back(fef::FieldPositionsIterator::UNKNOWN_LENGTH); // default last pos
        values.push_back(0.0f); // number of hits
        return stash.create<ValueExecutor>(values);
    }
    if (_type == 1.0) {  // index
        return stash.create<IndexFieldInfoExecutor>(_type, _isFilter, _fieldId, fieldHandle);
    } else if (_type == 2.0) {  // attribute
        return stash.create<AttrFieldInfoExecutor>(_type, fieldHandle);
    }
    std::vector<feature_t> values;
    values.push_back(_type);
    values.push_back(_isFilter);
    values.push_back(1.0f); // searched
    values.push_back(0.0f); // no hit
    values.push_back(fef::FieldPositionsIterator::UNKNOWN_LENGTH); // default field length
    values.push_back(fef::FieldPositionsIterator::UNKNOWN_LENGTH); // default first pos
    values.push_back(fef::FieldPositionsIterator::UNKNOWN_LENGTH); // default last pos
    values.push_back(0.0f); // number of hits
    return stash.create<ValueExecutor>(values);
}

} // namespace features
} // namespace search
