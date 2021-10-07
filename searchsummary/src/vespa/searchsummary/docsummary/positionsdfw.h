// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributedfw.h"
#include <vespa/searchlib/common/geo_location_spec.h>

namespace search::docsummary {

class LocationAttrDFW : public AttrDFW
{
public:
    using GeoLoc = search::common::GeoLocation;

    LocationAttrDFW(const vespalib::string & attrName)
        : AttrDFW(attrName)
    {}

    struct AllLocations {
        std::vector<const GeoLoc *> matching;
        std::vector<const GeoLoc *> other;

        ~AllLocations() {}

        bool empty() const {
            return matching.empty() && other.empty();
        }
        const std::vector<const GeoLoc *> & best() const {
            return matching.empty() ? other : matching;
        }
    };
    AllLocations getAllLocations(GetDocsumsState *state);
};

class AbsDistanceDFW : public LocationAttrDFW
{
private:
    uint64_t findMinDistance(uint32_t docid, GetDocsumsState *state,
                             const std::vector<const GeoLoc *> &locations);
public:
    AbsDistanceDFW(const vespalib::string & attrName);

    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GetDocsumsState *state,
                     ResType type, vespalib::slime::Inserter &target) override;
};

//--------------------------------------------------------------------------

class PositionsDFW : public AttrDFW
{
public:
    typedef std::unique_ptr<PositionsDFW> UP;

    PositionsDFW(const vespalib::string & attrName);

    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target) override;
};

PositionsDFW::UP createPositionsDFW(const char *attribute_name, IAttributeManager *index_man);
AbsDistanceDFW::UP createAbsDistanceDFW(const char *attribute_name, IAttributeManager *index_man);

}
