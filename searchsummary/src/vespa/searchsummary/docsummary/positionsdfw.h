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

    virtual bool IsGenerated() const { return true; }
    virtual uint32_t WriteField(uint32_t docid,
                                GeneralResult *gres,
                                GetDocsumsState *state,
                                ResType type,
                                search::RawBuf *target);
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target);
};

//--------------------------------------------------------------------------

class PositionsDFW : public AttrDFW
{
private:
    vespalib::asciistream formatField(const attribute::IAttributeVector & v, uint32_t docid, ResType type);

public:
    typedef std::unique_ptr<PositionsDFW> UP;

    PositionsDFW(const vespalib::string & attrName);

    virtual bool IsGenerated() const { return true; }
    virtual uint32_t WriteField(uint32_t docid,
                                GeneralResult *gres,
                                GetDocsumsState *state,
                                ResType type,
                                search::RawBuf *target);
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target);
};

PositionsDFW::UP createPositionsDFW(const char *attribute_name,
                                    IAttributeManager *index_man);

AbsDistanceDFW::UP createAbsDistanceDFW(const char *attribute_name,
                                        IAttributeManager *index_man);

}  // namespace docsummary
}  // namespace search

