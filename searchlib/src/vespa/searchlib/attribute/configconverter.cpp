// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configconverter.h"

using namespace vespa::config::search;
using namespace search;


namespace {

using search::attribute::CollectionType;
using search::attribute::BasicType;
using vespalib::eval::ValueType;

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

static DataTypeMap _dataTypeMap = getDataTypeMap();
static CollectionTypeMap _collectionTypeMap = getCollectionTypeMap();

}

namespace search::attribute {

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
    retval.setHuge(cfg.huge);
    retval.setEnableBitVectors(cfg.enablebitvectors);
    retval.setEnableOnlyBitVector(cfg.enableonlybitvector);
    retval.setIsFilter(cfg.enableonlybitvector);
    retval.setFastAccess(cfg.fastaccess);
    retval.setMutable(cfg.ismutable);
    predicateParams.setArity(cfg.arity);
    predicateParams.setBounds(cfg.lowerbound, cfg.upperbound);
    predicateParams.setDensePostingListThreshold(cfg.densepostinglistthreshold);
    retval.setPredicateParams(predicateParams);
    if (cfg.index.hnsw.enabled) {
        using CfgDm = AttributesConfig::Attribute::Index::Hnsw::Distancemetric;
        DistanceMetric dm;
        switch (cfg.index.hnsw.distancemetric) {
        case CfgDm::EUCLIDEAN:
            dm = DistanceMetric::Euclidean;
            break;
        case CfgDm::ANGULAR:
            dm = DistanceMetric::Angular;
            break;
        case CfgDm::GEODEGREES:
            dm = DistanceMetric::GeoDegrees;
            break;
        }
        retval.set_hnsw_index_params(HnswIndexParams(cfg.index.hnsw.maxlinkspernode,
                                                     cfg.index.hnsw.neighborstoexploreatinsert,
                                                     dm));
    }
    if (retval.basicType().type() == BasicType::Type::TENSOR) {
        if (!cfg.tensortype.empty()) {
            retval.setTensorType(ValueType::from_spec(cfg.tensortype));
        } else {
            retval.setTensorType(ValueType::tensor_type({}));
        }
    }
    return retval;
}

}
