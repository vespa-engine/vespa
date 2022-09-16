// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributedfw.h"

namespace search::docsummary {

/**
 * This is the docsum field writer used to extract the position (as a string) from a zcurve attribute
 **/
class GeoPositionDFW : public AttrDFW
{
private:
    bool _useV8geoPositions;
public:
    using UP = std::unique_ptr<GeoPositionDFW>;
    GeoPositionDFW(const vespalib::string & attrName, bool useV8geoPositions);
    void insertField(uint32_t docid, GetDocsumsState& state, vespalib::slime::Inserter &target) const override;
    static UP create(const char *attribute_name, const IAttributeManager *attribute_manager, bool useV8geoPositions);
};

}

