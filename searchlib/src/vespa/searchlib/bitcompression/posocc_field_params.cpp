// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posocc_field_params.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/index/postinglistparams.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".posocc_field_params");

using vespalib::GenericHeader;

namespace search::bitcompression {

namespace schema = search::index::schema;

PosOccFieldParams::PosOccFieldParams()
    : _elemLenK(0),
      _hasElements(false),
      _hasElementWeights(false),
      _avgElemLen(512),
      _collectionType(SINGLE),
      _name()
{ }


bool
PosOccFieldParams::operator==(const PosOccFieldParams &rhs) const
{
    return _collectionType == rhs._collectionType &&
              _avgElemLen == rhs._avgElemLen &&
                     _name == rhs._name;
}


vespalib::string
PosOccFieldParams::getParamsPrefix(uint32_t idx)
{
    vespalib::asciistream paramsPrefix;
    paramsPrefix << "fieldParams.";
    paramsPrefix << idx;
    return paramsPrefix.str();
}


void
PosOccFieldParams::getParams(PostingListParams &params, uint32_t idx) const
{
    vespalib::string paramsPrefix = getParamsPrefix(idx);
    vespalib::string collStr = paramsPrefix + ".collectionType";
    vespalib::string avgElemLenStr = paramsPrefix + ".avgElemLen";
    vespalib::string nameStr = paramsPrefix + ".name";

    switch (_collectionType) {
    case SINGLE:
        params.setStr(collStr, "single");
        break;
    case ARRAY:
        params.setStr(collStr, "array");
        break;
    case WEIGHTEDSET:
        params.setStr(collStr, "weightedSet");
        break;
    }
    params.set(avgElemLenStr, _avgElemLen);
    params.setStr(nameStr, _name);
}


void
PosOccFieldParams::setParams(const PostingListParams &params, uint32_t idx)
{
    vespalib::string paramsPrefix = getParamsPrefix(idx);
    vespalib::string collStr = paramsPrefix + ".collectionType";
    vespalib::string avgElemLenStr = paramsPrefix + ".avgElemLen";
    vespalib::string nameStr = paramsPrefix + ".name";

    if (params.isSet(collStr)) {
        vespalib::string collVal = params.getStr(collStr);
        if (collVal == "single") {
            _collectionType = SINGLE;
            _hasElements = false;
            _hasElementWeights = false;
        } else if (collVal == "array") {
            _collectionType = ARRAY;
            _hasElements = true;
            _hasElementWeights = false;
        } else if (collVal == "weightedSet") {
            _collectionType = WEIGHTEDSET;
            _hasElements = true;
            _hasElementWeights = true;
        }
    }
    params.get(avgElemLenStr, _avgElemLen);
    if (params.isSet(nameStr)) {
        _name = params.getStr(nameStr);
    }
}


void
PosOccFieldParams::setSchemaParams(const Schema &schema, uint32_t fieldId)
{
    assert(fieldId < schema.getNumIndexFields());
    const Schema::IndexField &field = schema.getIndexField(fieldId);
    switch (field.getCollectionType()) {
    case schema::CollectionType::SINGLE:
        _collectionType = SINGLE;
        _hasElements = false;
        _hasElementWeights = false;
        break;
    case schema::CollectionType::ARRAY:
        _collectionType = ARRAY;
        _hasElements = true;
        _hasElementWeights = false;
        break;
    case schema::CollectionType::WEIGHTEDSET:
        _collectionType = WEIGHTEDSET;
        _hasElements = true;
        _hasElementWeights = true;
        break;
    default:
        LOG(error, "Bad collection type");
        LOG_ABORT("should not be reached");
    }
    _avgElemLen = field.getAvgElemLen();
    _name = field.getName();
}


void
PosOccFieldParams::readHeader(const vespalib::GenericHeader &header,
                              const vespalib::string &prefix)
{
    vespalib::string nameKey(prefix + "fieldName");
    vespalib::string collKey(prefix + "collectionType");
    vespalib::string avgElemLenKey(prefix + "avgElemLen");
    _name = header.getTag(nameKey).asString();
    Schema::CollectionType ct = schema::collectionTypeFromName(header.getTag(collKey).asString());
    switch (ct) {
    case schema::CollectionType::SINGLE:
        _collectionType = SINGLE;
        _hasElements = false;
        _hasElementWeights = false;
        break;
    case schema::CollectionType::ARRAY:
        _collectionType = ARRAY;
        _hasElements = true;
        _hasElementWeights = false;
        break;
    case schema::CollectionType::WEIGHTEDSET:
        _collectionType = WEIGHTEDSET;
        _hasElements = true;
        _hasElementWeights = true;
        break;
    default:
        LOG_ABORT("Bad collection type when reading field param in header");
    }
    _avgElemLen = header.getTag(avgElemLenKey).asInteger();
}


void
PosOccFieldParams::writeHeader(vespalib::GenericHeader &header,
                               const vespalib::string &prefix) const
{
    vespalib::string nameKey(prefix + "fieldName");
    vespalib::string collKey(prefix + "collectionType");
    vespalib::string avgElemLenKey(prefix + "avgElemLen");
    header.putTag(GenericHeader::Tag(nameKey, _name));
    Schema::CollectionType ct(schema::CollectionType::SINGLE);
    switch (_collectionType) {
    case SINGLE:
        ct = schema::CollectionType::SINGLE;
        break;
    case ARRAY:
        ct = schema::CollectionType::ARRAY;
        break;
    case WEIGHTEDSET:
        ct = schema::CollectionType::WEIGHTEDSET;
        break;
    default:
        LOG_ABORT("Bad collection type when writing field param in header");
    }
    header.putTag(GenericHeader::Tag(collKey, schema::getTypeName(ct)));
    header.putTag(GenericHeader::Tag(avgElemLenKey, _avgElemLen));
}

}
