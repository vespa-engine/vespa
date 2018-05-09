// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geoposdfw.h"
#include <vespa/searchlib/common/documentlocations.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <climits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.geoposdfw");

namespace search::docsummary {

using attribute::IAttributeVector;
using attribute::IAttributeContext;

GeoPositionDFW::GeoPositionDFW(const vespalib::string & attrName) :
    AttrDFW(attrName)
{ }

namespace {

void fmtZcurve(int64_t zval, vespalib::slime::Inserter &target)
{
    int32_t docx = 0;
    int32_t docy = 0;
    vespalib::geo::ZCurve::decode(zval, &docx, &docy);
    if (docx == 0 && docy == INT_MIN) {
        LOG(spam, "skipping empty zcurve value");
    } else {
        vespalib::slime::Cursor &obj = target.insertObject();
        obj.setLong("y", docy);
        obj.setLong("x", docx);
    }
}

}

void
GeoPositionDFW::insertField(uint32_t docid, GeneralResult *, GetDocsumsState * dsState,
                            ResType, vespalib::slime::Inserter &target)
{
    using vespalib::slime::Cursor;
    using vespalib::slime::ObjectSymbolInserter;
    using vespalib::slime::Symbol;
    using vespalib::slime::ArrayInserter;

    const IAttributeVector & attribute = vec(*dsState);
    if (attribute.hasMultiValue()) {
        uint32_t entries = attribute.getValueCount(docid);
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
                fmtZcurve(pos, obj);
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
                fmtZcurve(pos, obj);
            }
        }
    } else {
        int64_t pos = attribute.getInt(docid);
        fmtZcurve(pos, target);
    }
}

GeoPositionDFW::UP
GeoPositionDFW::create(const char *attribute_name,
                       IAttributeManager *attribute_manager)
{
    GeoPositionDFW::UP ret;
    if (attribute_manager != nullptr) {
        if (!attribute_name) {
            LOG(warning, "create: missing attribute name '%p'", attribute_name);
            return ret;
        }
        IAttributeContext::UP context = attribute_manager->createContext();
        if (!context.get()) {
            LOG(warning, "create: could not create context from attribute manager");
            return ret;
        }
        const IAttributeVector *attribute = context->getAttribute(attribute_name);
        if (!attribute) {
            LOG(warning, "create: could not get attribute '%s' from context", attribute_name);
            return ret;
        }
    }
    ret.reset(new GeoPositionDFW(attribute_name));
    return ret;
}

}
