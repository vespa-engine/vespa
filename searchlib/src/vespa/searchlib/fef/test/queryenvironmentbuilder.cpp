// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryenvironmentbuilder.h"

namespace search::fef::test {

QueryEnvironmentBuilder::QueryEnvironmentBuilder(QueryEnvironment &env,
                                                 MatchDataLayout &layout) :
    _queryEnv(env),
    _layout(layout)
{
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
        if (info == nullptr || info->type() != FieldType::INDEX) {
            return nullptr;
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
    if (info == nullptr || info->type() != FieldType::ATTRIBUTE) {
        return nullptr;
    }
    return add_node(*info);
}

SimpleTermData *
QueryEnvironmentBuilder::add_virtual_node(const vespalib::string &virtual_field)
{
    const auto *info = _queryEnv.getIndexEnv()->getFieldByName(virtual_field);
    if (info == nullptr || info->type() != FieldType::VIRTUAL) {
        return nullptr;
    }
    return add_node(*info);
}

SimpleTermData *
QueryEnvironmentBuilder::add_node(const FieldInfo &info)
{
    _queryEnv.getTerms().push_back(SimpleTermData());
    SimpleTermData &td = _queryEnv.getTerms().back();
    td.setWeight(search::query::Weight(100));
    SimpleTermFieldData &tfd = td.addField(info.id());
    tfd.setHandle(_layout.allocTermField(tfd.getFieldId()));
    return &td;
}

QueryEnvironmentBuilder&
QueryEnvironmentBuilder::set_avg_field_length(const vespalib::string& field_name, double avg_field_length)
{
    _queryEnv.get_avg_field_lengths()[field_name] = avg_field_length;
    return *this;
}

}
