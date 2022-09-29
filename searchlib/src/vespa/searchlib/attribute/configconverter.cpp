// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configconverter.h"
#include <vespa/searchcommon/attribute/config.h>

using namespace vespa::config::search;

namespace search::attribute {

namespace {

using vespalib::eval::ValueType;
using vespalib::eval::CellType;

typedef std::map<AttributesConfig::Attribute::Datatype, BasicType::Type> DataTypeMap;
typedef std::map<AttributesConfig::Attribute::Collectiontype, CollectionType::Type> CollectionTypeMap;

DataTypeMap
getDataTypeMap()
{
    DataTypeMap map;
    map[AttributesConfig::Attribute::Datatype::STRING] = BasicType::STRING;
    map[AttributesConfig::Attribute::Datatype::BOOL] = BasicType::BOOL;
    map[AttributesConfig::Attribute::Datatype::UINT2] = BasicType::UINT2;
    map[AttributesConfig::Attribute::Datatype::UINT4] = BasicType::UINT4;
    map[AttributesConfig::Attribute::Datatype::INT8] = BasicType::INT8;
    map[AttributesConfig::Attribute::Datatype::INT16] = BasicType::INT16;
    map[AttributesConfig::Attribute::Datatype::INT32] = BasicType::INT32;
    map[AttributesConfig::Attribute::Datatype::INT64] = BasicType::INT64;
    map[AttributesConfig::Attribute::Datatype::FLOAT] = BasicType::FLOAT;
    map[AttributesConfig::Attribute::Datatype::DOUBLE] = BasicType::DOUBLE;
    map[AttributesConfig::Attribute::Datatype::PREDICATE] = BasicType::PREDICATE;
    map[AttributesConfig::Attribute::Datatype::TENSOR] = BasicType::TENSOR;
    map[AttributesConfig::Attribute::Datatype::REFERENCE] = BasicType::REFERENCE;
    map[AttributesConfig::Attribute::Datatype::NONE] = BasicType::NONE;
    return map;
}

CollectionTypeMap
getCollectionTypeMap()
{
    CollectionTypeMap map;
    map[AttributesConfig::Attribute::Collectiontype::SINGLE] = CollectionType::SINGLE;
    map[AttributesConfig::Attribute::Collectiontype::ARRAY] = CollectionType::ARRAY;
    map[AttributesConfig::Attribute::Collectiontype::WEIGHTEDSET] = CollectionType::WSET;
    return map;
}

DataTypeMap _dataTypeMap = getDataTypeMap();
CollectionTypeMap _collectionTypeMap = getCollectionTypeMap();

DictionaryConfig::Type
convert(AttributesConfig::Attribute::Dictionary::Type type_cfg) {
    switch (type_cfg) {
    case AttributesConfig::Attribute::Dictionary::Type::BTREE:
        return DictionaryConfig::Type::BTREE;
    case AttributesConfig::Attribute::Dictionary::Type::HASH:
        return DictionaryConfig::Type::HASH;
    case AttributesConfig::Attribute::Dictionary::Type::BTREE_AND_HASH:
        return DictionaryConfig::Type::BTREE_AND_HASH;
    }
    assert(false);
}

DictionaryConfig::Match
convert(AttributesConfig::Attribute::Dictionary::Match match_cfg) {
    switch (match_cfg) {
    case AttributesConfig::Attribute::Dictionary::Match::CASE_SENSITIVE:
    case AttributesConfig::Attribute::Dictionary::Match::CASED:
        return DictionaryConfig::Match::CASED;
    case AttributesConfig::Attribute::Dictionary::Match::CASE_INSENSITIVE:
    case AttributesConfig::Attribute::Dictionary::Match::UNCASED:
        return DictionaryConfig::Match::UNCASED;
    }
    assert(false);
}

DictionaryConfig
convert_dictionary(const AttributesConfig::Attribute::Dictionary & dictionary) {
    return {convert(dictionary.type), convert(dictionary.match)};
}

Config::Match
convertMatch(AttributesConfig::Attribute::Match match_cfg) {
    switch (match_cfg) {
        case AttributesConfig::Attribute::Match::CASED:
            return Config::Match::CASED;
        case AttributesConfig::Attribute::Match::UNCASED:
            return Config::Match::UNCASED;
    }
    assert(false);
}

}

Config
ConfigConverter::convert(const AttributesConfig::Attribute & cfg)
{
    BasicType bType(_dataTypeMap[cfg.datatype]);
    CollectionType cType(_collectionTypeMap[cfg.collectiontype]);
    cType.removeIfZero(cfg.removeifzero);
    cType.createIfNonExistant(cfg.createifnonexistent);
    Config retval(bType, cType);
    PredicateParams predicateParams;
    retval.setFastSearch(cfg.fastsearch);
    retval.setEnableOnlyBitVector(cfg.enableonlybitvector);
    retval.setIsFilter(cfg.enableonlybitvector);
    retval.setFastAccess(cfg.fastaccess);
    retval.setMutable(cfg.ismutable);
    retval.setPaged(cfg.paged);
    retval.setMaxUnCommittedMemory(cfg.maxuncommittedmemory);
    predicateParams.setArity(cfg.arity);
    predicateParams.setBounds(cfg.lowerbound, cfg.upperbound);
    predicateParams.setDensePostingListThreshold(cfg.densepostinglistthreshold);
    retval.setPredicateParams(predicateParams);
    retval.set_dictionary_config(convert_dictionary(cfg.dictionary));
    retval.set_match(convertMatch(cfg.match));
    using CfgDm = AttributesConfig::Attribute::Distancemetric;
    DistanceMetric dm(DistanceMetric::Euclidean);
    switch (cfg.distancemetric) {
        case CfgDm::EUCLIDEAN:
            dm = DistanceMetric::Euclidean;
            break;
        case CfgDm::ANGULAR:
            dm = DistanceMetric::Angular;
            break;
        case CfgDm::GEODEGREES:
            dm = DistanceMetric::GeoDegrees;
            break;
        case CfgDm::INNERPRODUCT:
            dm = DistanceMetric::InnerProduct;
            break;
        case CfgDm::HAMMING:
            dm = DistanceMetric::Hamming;
            break;
    }
    retval.set_distance_metric(dm);
    if (cfg.index.hnsw.enabled) {
        retval.set_hnsw_index_params(HnswIndexParams(cfg.index.hnsw.maxlinkspernode,
                                                     cfg.index.hnsw.neighborstoexploreatinsert,
                                                     dm, cfg.index.hnsw.multithreadedindexing));
    }
    if (retval.basicType().type() == BasicType::Type::TENSOR) {
        if (!cfg.tensortype.empty()) {
            retval.setTensorType(ValueType::from_spec(cfg.tensortype));
        } else {
            retval.setTensorType(ValueType::double_type());
        }
    }
    return retval;
}

}
