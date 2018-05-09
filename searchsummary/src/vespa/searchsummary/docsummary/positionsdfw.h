// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributedfw.h"

namespace search::docsummary {

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

}
