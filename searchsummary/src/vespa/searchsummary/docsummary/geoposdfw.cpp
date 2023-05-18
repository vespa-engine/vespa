// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geoposdfw.h"
#include <vespa/searchlib/common/documentlocations.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/issue.h>
#include <climits>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.geoposdfw");

namespace search::docsummary {

using attribute::IAttributeVector;
using attribute::IAttributeContext;
using vespalib::Issue;

GeoPositionDFW::GeoPositionDFW(const vespalib::string & attrName, bool useV8geoPositions) :
    AttrDFW(attrName),
    _useV8geoPositions(useV8geoPositions)
{ }

namespace {

double to_degrees(int32_t microDegrees) {
    double d = microDegrees / 1.0e6;
    return d;
}

void fmtZcurve(int64_t zval, vespalib::slime::Inserter &target, bool useV8geoPositions) {
    int32_t docx = 0;
    int32_t docy = 0;
    vespalib::geo::ZCurve::decode(zval, &docx, &docy);
    if (docx == 0 && docy == INT_MIN) {
        LOG(spam, "skipping empty zcurve value");
    } else {
        vespalib::slime::Cursor &obj = target.insertObject();
        if (useV8geoPositions) {
            double degrees_ns = to_degrees(docy);
            double degrees_ew = to_degrees(docx);
            obj.setDouble("lat", degrees_ns);
            obj.setDouble("lng", degrees_ew);
        } else {
            obj.setLong("y", docy);
            obj.setLong("x", docx);
        }
    }
}

}

void
GeoPositionDFW::insertField(uint32_t docid, GetDocsumsState& dsState, vespalib::slime::Inserter &target) const
{
    using vespalib::slime::Cursor;
    using vespalib::slime::ObjectSymbolInserter;
    using vespalib::slime::Symbol;
    using vespalib::slime::ArrayInserter;

    const auto& attribute = get_attribute(dsState);
    if (attribute.hasMultiValue()) {
        uint32_t entries = attribute.getValueCount(docid);
        if (entries == 0 && _useV8geoPositions) return;
        Cursor &arr = target.insertArray();
        if (attribute.hasWeightedSetType()) {
            Symbol isym = arr.resolve("item");
            Symbol wsym = arr.resolve("weight");
            std::vector<IAttributeVector::WeightedInt> elements(entries);
            entries = attribute.get(docid, &elements[0], entries);
            for (uint32_t i = 0; i < entries; ++i) {
                Cursor &elem = arr.addObject();
                int64_t pos = elements[i].getValue();
                ObjectSymbolInserter obj(elem, isym);
                fmtZcurve(pos, obj, _useV8geoPositions);
                elem.setLong(wsym, elements[i].getWeight());
            }
        } else {
            std::vector<IAttributeVector::largeint_t> elements(16);
            uint32_t numValues = attribute.get(docid, &elements[0], elements.size());
            if (numValues > elements.size()) {
                elements.resize(numValues);
                numValues = attribute.get(docid, &elements[0], elements.size());
                assert(numValues <= elements.size());
            }
            for (uint32_t i = 0; i < numValues; i++) {
                int64_t pos = elements[i];
                ArrayInserter obj(arr);
                fmtZcurve(pos, obj, _useV8geoPositions);
            }
        }
    } else {
        int64_t pos = attribute.getInt(docid);
        fmtZcurve(pos, target, _useV8geoPositions);
    }
}

GeoPositionDFW::UP
GeoPositionDFW::create(const char *attribute_name,
                       const IAttributeManager *attribute_manager,
                       bool useV8geoPositions)
{
    if (attribute_manager != nullptr) {
        if (!attribute_name) {
            LOG(warning, "create: missing attribute name '%p'", attribute_name);
            return {};
        }
        IAttributeContext::UP context = attribute_manager->createContext();
        if (!context.get()) {
            LOG(warning, "create: could not create context from attribute manager");
            return {};
        }
        const IAttributeVector *attribute = context->getAttribute(attribute_name);
        if (!attribute) {
            Issue::report("GeoPositionDFW::create: could not get attribute '%s' from context", attribute_name);
            return {};
        }
    }
    return std::make_unique<GeoPositionDFW>(attribute_name, useV8geoPositions);
}

}
