// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryenvironmentbuilder.h"

namespace search {
namespace fef {
namespace test {

QueryEnvironmentBuilder::QueryEnvironmentBuilder(QueryEnvironment &env,
                                                 MatchDataLayout &layout) :
    _queryEnv(env),
    _layout(layout)
{
    // empty
}

QueryEnvironmentBuilder::~QueryEnvironmentBuilder() { }

SimpleTermData &
QueryEnvironmentBuilder::addAllFields()
{
    _queryEnv.getTerms().push_back(SimpleTermData());
    SimpleTermData &td = _queryEnv.getTerms().back();
    td.setWeight(search::query::Weight(100));
    const IIndexEnvironment &idxEnv = *_queryEnv.getIndexEnv();
    for (uint32_t i = 0; i < idxEnv.getNumFields(); ++i) {
        const FieldInfo *info = idxEnv.getField(i);
        SimpleTermFieldData &tfd = td.addField(info->id());
        tfd.setHandle(_layout.allocTermField(tfd.getFieldId()));
    }
    return td;
}

SimpleTermData *
QueryEnvironmentBuilder::addIndexNode(const std::vector<vespalib::string> &fieldNames)
{
    _queryEnv.getTerms().push_back(SimpleTermData());
    SimpleTermData &td = _queryEnv.getTerms().back();
    td.setWeight(search::query::Weight(100));
    for (uint32_t i = 0; i < fieldNames.size(); ++i) {
        const FieldInfo *info = _queryEnv.getIndexEnv()->getFieldByName(fieldNames[i]);
        if (info == NULL || info->type() != FieldType::INDEX) {
            return NULL;
        }
        SimpleTermFieldData &tfd = td.addField(info->id());
        tfd.setHandle(_layout.allocTermField(tfd.getFieldId()));
    }
    return &td;
}

SimpleTermData *
QueryEnvironmentBuilder::addAttributeNode(const vespalib::string &attrName)
{
    const FieldInfo *info = _queryEnv.getIndexEnv()->getFieldByName(attrName);
    if (info == NULL || info->type() != FieldType::ATTRIBUTE) {
        return NULL;
    }
    _queryEnv.getTerms().push_back(SimpleTermData());
    SimpleTermData &td = _queryEnv.getTerms().back();
    td.setWeight(search::query::Weight(100));
    SimpleTermFieldData &tfd = td.addField(info->id());
    tfd.setHandle(_layout.allocTermField(tfd.getFieldId()));
    return &td;
}

} // namespace test
} // namespace fef
} // namespace search
