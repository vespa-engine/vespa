// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldsearcher.h"
#include <vespa/searchlib/common/geo_location.h>

namespace vsm {

class GeoPosFieldSearcher : public FieldSearcher {
public:
    GeoPosFieldSearcher(FieldIdT fId=0);
    ~GeoPosFieldSearcher();
    void prepare(search::streaming::QueryTermList & qtl, const SharedSearcherBuf & buf) override;
    void onValue(const document::FieldValue & fv) override;
    void onStructValue(const document::StructFieldValue & fv) override;
    std::unique_ptr<FieldSearcher> duplicate() const override;
protected:
    using GeoLocation = search::common::GeoLocation;
    class GeoPosInfo : public GeoLocation {
    public:
        GeoPosInfo (GeoLocation loc) noexcept : GeoLocation(std::move(loc)) {}
        bool cmp(const document::StructFieldValue & fv) const;
    };
    typedef std::vector<GeoPosInfo> GeoPosInfoListT;
    GeoPosInfoListT _geoPosTerm;
};

}
