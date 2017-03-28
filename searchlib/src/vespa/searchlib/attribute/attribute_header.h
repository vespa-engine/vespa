// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcommon/attribute/predicate_params.h>

namespace search {
namespace attribute {

/**
 * Attribute header class used by actual IAttributeTarget implementations.
 **/
class AttributeHeader {
private:
    vespalib::string _fileName;
    vespalib::string _basicType;
    vespalib::string _collectionType;
    vespalib::string _tensorType;
    bool        _hasMultiValue;
    bool        _hasWeightedSetType;
    bool        _enumerated;
    attribute::PersistentPredicateParams _predicateParams;
    uint32_t    _numDocs;
    uint32_t    _fixedWidth;
    uint64_t    _uniqueValueCount;
    uint64_t    _totalValueCount;
    uint64_t    _createSerialNum;
    uint32_t    _version;
public:
    AttributeHeader();
    AttributeHeader(const vespalib::string &fileName,
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
                    uint32_t version);
    ~AttributeHeader();

    const vespalib::string & getFileName() const { return _fileName; }
    const vespalib::string & getBasicType() const { return _basicType; }
    const vespalib::string &getCollectionType() const { return _collectionType; }
    const vespalib::string &getTensorType() const { return _tensorType; }
    bool hasMultiValue() const { return _hasMultiValue; }
    bool hasWeightedSetType() const { return _hasWeightedSetType; }
    uint32_t getNumDocs() const { return _numDocs; }
    size_t getFixedWidth() const { return _fixedWidth; }
    uint64_t getUniqueValueCount(void) const { return _uniqueValueCount; }
    uint64_t getTotalValueCount(void) const { return _totalValueCount; }
    bool getEnumerated(void) const { return _enumerated; }
    uint64_t getCreateSerialNum(void) const { return _createSerialNum; }
    uint32_t getVersion() const  { return _version; }
    const attribute::PersistentPredicateParams &getPredicateParams() const { return _predicateParams; }
};

} // namespace search::attribute
} // namespace search
