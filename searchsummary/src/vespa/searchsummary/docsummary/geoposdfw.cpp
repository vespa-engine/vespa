// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geoposdfw.h"
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/common/documentlocations.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <vespa/vespalib/data/slime/cursor.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.geoposdfw");

namespace search {
namespace docsummary {

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

void fmtZcurve(int64_t zval, vespalib::JSONWriter json)
{
    int32_t docx = 0;
    int32_t docy = 0;
    vespalib::geo::ZCurve::decode(zval, &docx, &docy);
    json.beginObject();
    json.appendKey("y"); json.appendInt64(docy);
    json.appendKey("x"); json.appendInt64(docx);
    json.endObject();
}

} // namespace <unnamed>

vespalib::asciistream
GeoPositionDFW::formatField(const IAttributeVector & attribute, uint32_t docid)
{
    vespalib::asciistream target;
    vespalib::JSONWriter json(target);

    if (attribute.hasMultiValue()) {
        uint32_t entries = attribute.getValueCount(docid);
        LOG(debug, "docid=%d, entries=%d", docid, entries);
        json.beginArray();
        if (attribute.hasWeightedSetType()) {
            std::vector<IAttributeVector::WeightedInt> elements(entries);
            entries = attribute.get(docid, &elements[0], entries);
            for (uint32_t i = 0; i < entries; ++i) {
                json.beginObject();
                int64_t pos = elements[i].getValue();
                json.appendKey("item");
                fmtZcurve(pos, json);
                json.appendKey("weight");
                json.appendInt64(elements[i].getWeight());
                json.endObject();
            }
        } else {
            std::vector<IAttributeVector::largeint_t> elements(16);
            uint32_t numValues = attribute.get(docid, &elements[0], elements.size());
            if (numValues > elements.size()) {
                elements.resize(numValues);
                numValues = attribute.get(docid, &elements[0], elements.size());
                assert(numValues <= elements.size());
            }
            LOG(debug, "docid=%d, numValues=%d", docid, numValues);
            for (uint32_t i = 0; i < numValues; i++) {
                int64_t pos = elements[i];
                fmtZcurve(pos, json);
            }
        }
    } else {
        int64_t pos = attribute.getInt(docid);
        LOG(debug, "docid=%d, pos=%ld", docid, pos);
        fmtZcurve(pos, json);
    }
    return target;
}

void
GeoPositionDFW::insertField(uint32_t docid,
                            GeneralResult *,
                            GetDocsumsState * dsState,
                            ResType,
                            vespalib::slime::Inserter &target)
{
    using vespalib::slime::Cursor;
    using vespalib::slime::ObjectInserter;
    using vespalib::slime::ArrayInserter;

    const IAttributeVector & attribute = vec(*dsState);
    if (attribute.hasMultiValue()) {
        uint32_t entries = attribute.getValueCount(docid);
        Cursor &arr = target.insertArray();
        if (attribute.hasWeightedSetType()) {
            std::vector<IAttributeVector::WeightedInt> elements(entries);
            entries = attribute.get(docid, &elements[0], entries);
            for (uint32_t i = 0; i < entries; ++i) {
                Cursor &elem = arr.addObject();
                int64_t pos = elements[i].getValue();
                ObjectInserter obj(elem, "item");
                fmtZcurve(pos, obj);
                elem.setLong("weight", elements[i].getWeight());
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

uint32_t
GeoPositionDFW::WriteField(uint32_t docid,
                           GeneralResult *,
                           GetDocsumsState * dsState,
                           ResType type,
                           search::RawBuf * target)
{
    int str_len_ofs = target->GetUsedLen();

    vespalib::asciistream val(formatField(vec(*dsState), docid));

    bool isLong = IsBinaryCompatible(type, RES_LONG_STRING);
    if (isLong) {
        uint32_t str_len_32 = val.size();
        target->append(&str_len_32, sizeof(str_len_32));
        target->append(val.c_str(), str_len_32);
    } else {
        uint16_t str_len_16 = val.size();
        target->append(&str_len_16, sizeof(str_len_16));
        target->append(val.c_str(), str_len_16);
    }
    // calculate number of bytes written
    uint32_t written = target->GetUsedLen() - str_len_ofs;
    return written;
}

GeoPositionDFW::UP
GeoPositionDFW::create(const char *attribute_name,
                       IAttributeManager *attribute_manager)
{
    GeoPositionDFW::UP ret;
    if (attribute_manager != NULL) {
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
}

