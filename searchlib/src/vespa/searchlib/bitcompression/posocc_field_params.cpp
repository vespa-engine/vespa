// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posocc_field_params.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/index/postinglistparams.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

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
      _name(),
      _field_length_info()
{ }


bool
PosOccFieldParams::operator==(const PosOccFieldParams &rhs) const
{
    return _collectionType == rhs._collectionType &&
              _avgElemLen == rhs._avgElemLen &&
                     _name == rhs._name;
}


std::string
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
    std::string paramsPrefix = getParamsPrefix(idx);
    std::string collStr = paramsPrefix + ".collectionType";
    std::string avgElemLenStr = paramsPrefix + ".avgElemLen";
    std::string nameStr = paramsPrefix + ".name";

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
    std::string paramsPrefix = getParamsPrefix(idx);
    std::string collStr = paramsPrefix + ".collectionType";
    std::string avgElemLenStr = paramsPrefix + ".avgElemLen";
    std::string nameStr = paramsPrefix + ".name";

    if (params.isSet(collStr)) {
        std::string collVal = params.getStr(collStr);
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

namespace {

std::string field_length_infix = "field_length.";

struct FieldLengthKeys {
    std::string _average;
    std::string _samples;
    std::string _average_element_length;
    FieldLengthKeys(const std::string &prefix);
    ~FieldLengthKeys();
};

FieldLengthKeys::FieldLengthKeys(const std::string &prefix)
    : _average(prefix + field_length_infix + "average"),
      _samples(prefix + field_length_infix + "samples"),
      _average_element_length(prefix + field_length_infix + "average_element_length")
{
}

FieldLengthKeys::~FieldLengthKeys() = default;

}

void
PosOccFieldParams::readHeader(const GenericHeader &header,
                              const std::string &prefix)
{
    using Tag = GenericHeader::Tag;
    std::string nameKey(prefix + "fieldName");
    std::string collKey(prefix + "collectionType");
    std::string avgElemLenKey(prefix + "avgElemLen");
    FieldLengthKeys field_length_keys(prefix);

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
    if (header.hasTag(field_length_keys._average) &&
        header.hasTag(field_length_keys._samples)) {
        const auto &average_field_length_tag = header.getTag(field_length_keys._average);
        const auto &field_length_samples_tag = header.getTag(field_length_keys._samples);
        if (average_field_length_tag.getType() == Tag::Type::TYPE_FLOAT &&
            field_length_samples_tag.getType() == Tag::Type::TYPE_INTEGER) {
            double average_field_length = average_field_length_tag.asFloat();
            double average_element_length = average_field_length;
            if (header.hasTag(field_length_keys._average_element_length)) {
                const auto& average_element_length_tag = header.getTag(field_length_keys._average_element_length);
                if (average_element_length_tag.getType() == Tag::Type::TYPE_FLOAT) {
                    average_element_length = average_element_length_tag.asFloat();
                }
            }
            _field_length_info = index::FieldLengthInfo(average_field_length, average_element_length, field_length_samples_tag.asInteger());
        }
    }
}


void
PosOccFieldParams::writeHeader(GenericHeader &header,
                               const std::string &prefix) const
{
    using Tag = GenericHeader::Tag;
    std::string nameKey(prefix + "fieldName");
    std::string collKey(prefix + "collectionType");
    std::string avgElemLenKey(prefix + "avgElemLen");
    FieldLengthKeys field_length_keys(prefix);
    header.putTag(Tag(nameKey, _name));
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
    header.putTag(Tag(collKey, schema::getTypeName(ct)));
    header.putTag(Tag(avgElemLenKey, _avgElemLen));
    header.putTag(Tag(field_length_keys._average, _field_length_info.get_average_field_length()));
    header.putTag(Tag(field_length_keys._samples, static_cast<int64_t>(_field_length_info.get_num_samples())));
    header.putTag(Tag(field_length_keys._average_element_length, _field_length_info.get_average_element_length()));
}

}
