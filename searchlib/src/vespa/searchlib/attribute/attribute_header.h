// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/searchcommon/attribute/collectiontype.h>
#include <vespa/searchcommon/attribute/predicate_params.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib { class GenericHeader; }

namespace search::attribute {

/**
 * Attribute header class used by attribute savers and attribute initializer
 * to convert to/from generic header tags.
 **/
class AttributeHeader {
private:
    vespalib::string _fileName;
    BasicType _basicType;
    CollectionType _collectionType;
    vespalib::eval::ValueType _tensorType;
    bool        _enumerated;
    bool        _collectionTypeParamsSet;
    bool        _predicateParamsSet;
    PersistentPredicateParams _predicateParams;
    uint32_t    _numDocs;
    uint64_t    _uniqueValueCount;
    uint64_t    _totalValueCount;
    uint64_t    _createSerialNum;
    uint32_t    _version;

    void internalExtractTags(const vespalib::GenericHeader &header);
public:
    AttributeHeader();
    AttributeHeader(const vespalib::string &fileName);
    AttributeHeader(const vespalib::string &fileName,
                    BasicType basicType,
                    CollectionType collectionType,
                    const vespalib::eval::ValueType &tensorType,
                    bool enumerated,
                    const PersistentPredicateParams &predicateParams,
                    uint32_t numDocs,
                    uint32_t fixedWidth,
                    uint64_t uniqueValueCount,
                    uint64_t totalValueCount,
                    uint64_t createSerialNum,
                    uint32_t version);
    ~AttributeHeader();

    const vespalib::string & getFileName() const { return _fileName; }
    const BasicType & getBasicType() const { return _basicType; }
    const CollectionType &getCollectionType() const { return _collectionType; }
    const vespalib::eval::ValueType &getTensorType() const { return _tensorType; }
    bool hasMultiValue() const;
    bool hasWeightedSetType() const;
    uint32_t getNumDocs() const { return _numDocs; }
    bool getEnumerated() const { return _enumerated; }
    uint64_t getCreateSerialNum() const { return _createSerialNum; }
    uint32_t getVersion() const  { return _version; }
    const PersistentPredicateParams &getPredicateParams() const { return _predicateParams; }
    bool getPredicateParamsSet() const { return _predicateParamsSet; }
    bool getCollectionTypeParamsSet() const { return _collectionTypeParamsSet; }
    static AttributeHeader extractTags(const vespalib::GenericHeader &header);
    void addTags(vespalib::GenericHeader &header) const;
};

}
