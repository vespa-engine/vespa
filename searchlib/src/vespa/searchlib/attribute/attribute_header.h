// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/searchcommon/attribute/collectiontype.h>
#include <vespa/searchcommon/attribute/hnsw_index_params.h>
#include <vespa/searchcommon/attribute/predicate_params.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/stllike/string.h>
#include <optional>

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
    std::optional<HnswIndexParams> _hnsw_index_params;
    uint32_t    _numDocs;
    uint64_t    _uniqueValueCount;
    uint64_t    _totalValueCount;
    uint64_t    _createSerialNum;
    uint32_t    _version;
    vespalib::GenericHeader _extra_tags;

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
                    const std::optional<HnswIndexParams>& hnsw_index_params,
                    uint32_t numDocs,
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
    uint64_t get_total_value_count() const { return _totalValueCount; }
    uint64_t get_unique_value_count() const { return _uniqueValueCount; }
    const PersistentPredicateParams &getPredicateParams() const { return _predicateParams; }
    bool getPredicateParamsSet() const { return _predicateParamsSet; }
    bool getCollectionTypeParamsSet() const { return _collectionTypeParamsSet; }
    const std::optional<HnswIndexParams>& get_hnsw_index_params() const { return _hnsw_index_params; }
    static AttributeHeader extractTags(const vespalib::GenericHeader &header, const vespalib::string &file_name);
    void addTags(vespalib::GenericHeader &header) const;
    vespalib::GenericHeader& get_extra_tags() noexcept { return _extra_tags; }
};

}
