// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/attributedfw.h>

namespace search {
namespace docsummary {

class AbsDistanceDFW : public AttrDFW
{
private:
    uint64_t findMinDistance(uint32_t docid, GetDocsumsState *state);
public:
    AbsDistanceDFW(const vespalib::string & attrName);

    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GeneralResult *gres, GetDocsumsState *state,
                     ResType type, vespalib::slime::Inserter &target) override;
};

//--------------------------------------------------------------------------

class PositionsDFW : public AttrDFW
{
public:
    typedef std::unique_ptr<PositionsDFW> UP;

    PositionsDFW(const vespalib::string & attrName);

    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GeneralResult *gres, GetDocsumsState *state,
                     ResType type, vespalib::slime::Inserter &target) override ;
};

PositionsDFW::UP createPositionsDFW(const char *attribute_name, IAttributeManager *index_man);
AbsDistanceDFW::UP createAbsDistanceDFW(const char *attribute_name, IAttributeManager *index_man);

}  // namespace docsummary
}  // namespace search

