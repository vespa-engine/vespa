// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_header.h"

namespace search {
namespace attribute {

AttributeHeader::AttributeHeader()
    : _fileName(""),
      _basicType(""),
      _collectionType(""),
      _hasMultiValue(false),
      _hasWeightedSetType(false),
      _enumerated(false),
      _predicateParams(),
      _numDocs(0),
      _fixedWidth(0),
      _uniqueValueCount(0),
      _totalValueCount(0),
      _createSerialNum(0u),
      _version(0)
{
}

AttributeHeader::AttributeHeader(const vespalib::string &fileName,
                                 const vespalib::string &basicType,
                                 const vespalib::string &collectionType,
                                 const vespalib::string &tensorType,
                                 bool multiValue, bool weightedSetType,
                                 bool enumerated,
                                 const attribute::PersistentPredicateParams &predicateParams,
                                 uint32_t numDocs,
                                 uint32_t fixedWidth,
                                 uint64_t uniqueValueCount,
                                 uint64_t totalValueCount,
                                 uint64_t createSerialNum,
                                 uint32_t version)
    : _fileName(fileName),
      _basicType(basicType),
      _collectionType(collectionType),
      _tensorType(tensorType),
      _hasMultiValue(multiValue),
      _hasWeightedSetType(weightedSetType),
      _enumerated(enumerated),
      _predicateParams(predicateParams),
      _numDocs(numDocs),
      _fixedWidth(fixedWidth),
      _uniqueValueCount(uniqueValueCount),
      _totalValueCount(totalValueCount),
      _createSerialNum(createSerialNum),
      _version(version)
{
}

AttributeHeader::~AttributeHeader()
{
}

} // namespace search::attribute
} // namespace search
