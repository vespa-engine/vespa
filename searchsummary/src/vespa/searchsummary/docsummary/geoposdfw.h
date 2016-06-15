// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/attributedfw.h>

namespace search {
namespace docsummary {

/**
 * This is the docsum field writer used to extract the position (as a string) from a zcurve attribute
 **/
class GeoPositionDFW : public AttrDFW
{
private:
    vespalib::asciistream formatField(const attribute::IAttributeVector & v, uint32_t docid);
public:
    typedef std::unique_ptr<GeoPositionDFW> UP;
    GeoPositionDFW(const vespalib::string & attrName);
    virtual uint32_t WriteField(uint32_t docid,
                                GeneralResult * gres,
                                GetDocsumsState * state,
                                ResType type,
                                search::RawBuf * target);
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target);
    static UP create(const char *attribute_name,
                     IAttributeManager *attribute_manager);
};

}
}

