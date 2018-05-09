// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributedfw.h"

namespace search::docsummary {

/**
 * This is the docsum field writer used to extract the position (as a string) from a zcurve attribute
 **/
class GeoPositionDFW : public AttrDFW
{
public:
    typedef std::unique_ptr<GeoPositionDFW> UP;
    GeoPositionDFW(const vespalib::string & attrName);
    void insertField(uint32_t docid, GeneralResult *gres, GetDocsumsState *state,
                     ResType type, vespalib::slime::Inserter &target) override;
    static UP create(const char *attribute_name, IAttributeManager *attribute_manager);
};

}

