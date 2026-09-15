// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configconverter.h"

#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.configconverter");

using namespace vespa::config::search;

namespace search::attribute {

namespace {

using vespalib::eval::CellType;
using vespalib::eval::ValueType;

using DataTypeMap = std::map<AttributesConfig::Attribute::Datatype, BasicType::Type>;
using CollectionTypeMap = std::map<AttributesConfig::Attribute::Collectiontype, CollectionType::Type>;

DataTypeMap getDataTypeMap() {
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
    map[AttributesConfig::Attribute::Datatype::RAW] = BasicType::RAW;
    map[AttributesConfig::Attribute::Datatype::NONE] = BasicType::NONE;
    return map;
}

CollectionTypeMap getCollectionTypeMap() {
    CollectionTypeMap map;
    map[AttributesConfig::Attribute::Collectiontype::SINGLE] = CollectionType::SINGLE;
    map[AttributesConfig::Attribute::Collectiontype::ARRAY] = CollectionType::ARRAY;
    map[AttributesConfig::Attribute::Collectiontype::WEIGHTEDSET] = CollectionType::WSET;
    return map;
}

DataTypeMap       _dataTypeMap = getDataTypeMap();
CollectionTypeMap _collectionTypeMap = getCollectionTypeMap();

DictionaryConfig::Type convert(AttributesConfig::Attribute::Dictionary::Type type_cfg) {
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

DictionaryConfig::Match convert(AttributesConfig::Attribute::Dictionary::Match match_cfg) {
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

DictionaryConfig convert_dictionary(const AttributesConfig::Attribute::Dictionary& dictionary) {
    return {convert(dictionary.type), convert(dictionary.match)};
}

const char* match_name(DictionaryConfig::Match match) noexcept {
    return (match == DictionaryConfig::Match::CASED) ? "cased" : "uncased";
}

const char* type_name(DictionaryConfig::Type type) noexcept {
    switch (type) {
    case DictionaryConfig::Type::HASH:
        return "hash";
    case DictionaryConfig::Type::BTREE_AND_HASH:
        return "btree and hash";
    case DictionaryConfig::Type::BTREE:
        break;
    }
    return "btree";
}

/*
 * An uncased dictionary is folded, and a folded dictionary is btree only (make_enum_store_dictionary picks
 * EnumStoreFoldedDictionary, which has no hash dictionary, as soon as a folded comparator is given), so a
 * hash dictionary cannot be combined with uncased matching.
 */
DictionaryConfig::Type without_hash(DictionaryConfig::Type) noexcept {
    return DictionaryConfig::Type::BTREE;
}

DictionaryConfig::Match as_dictionary_match(Config::Match match) noexcept {
    return (match == Config::Match::CASED) ? DictionaryConfig::Match::CASED : DictionaryConfig::Match::UNCASED;
}

/*
 * The dictionary match setting decides how the enum store is ordered and whether the case variants of a
 * value share a single posting list, while the attribute match setting decides how the query side compares
 * terms. For a string attribute the two must agree: a cased search answered from the folded posting lists of
 * an uncased dictionary returns the wrong documents, and so does the opposite combination.
 *
 * They are kept in sync by the config model, so a mismatch here means the config is broken. Let the attribute
 * match setting win, since that is the one the query uses, and tell the operator to fix the schema.
 */
DictionaryConfig check_dictionary_match(const std::string& name, const DictionaryConfig& dictionary,
                                        Config::Match match) {
    auto wanted = as_dictionary_match(match);
    if (dictionary.getMatch() == wanted) {
        return dictionary;
    }
    auto type =
        (wanted == DictionaryConfig::Match::UNCASED) ? without_hash(dictionary.getType()) : dictionary.getType();
    LOG(error,
        "Attribute '%s' has match '%s' but dictionary match '%s'. Using a %s dictionary matched '%s' instead. "
        "These two settings must agree, fix them for this field in the schema.",
        name.c_str(), match_name(wanted), match_name(dictionary.getMatch()), type_name(type), match_name(wanted));
    return {type, wanted};
}

Config::Match convertMatch(AttributesConfig::Attribute::Match match_cfg) {
    switch (match_cfg) {
    case AttributesConfig::Attribute::Match::CASED:
        return Config::Match::CASED;
    case AttributesConfig::Attribute::Match::UNCASED:
        return Config::Match::UNCASED;
    }
    assert(false);
}

} // namespace

Config ConfigConverter::convert(const AttributesConfig::Attribute& cfg) {
    BasicType      bType(_dataTypeMap[cfg.datatype]);
    CollectionType cType(_collectionTypeMap[cfg.collectiontype]);
    cType.removeIfZero(cfg.removeifzero);
    cType.createIfNonExistant(cfg.createifnonexistent);
    Config          retval(bType, cType);
    PredicateParams predicateParams;
    retval.setFastSearch(cfg.fastsearch);
    retval.setIsFilter(cfg.enableonlybitvector);
    retval.setFastAccess(cfg.fastaccess);
    retval.setMutable(cfg.ismutable);
    retval.setPaged(cfg.paged);
    retval.setMaxUnCommittedMemory(cfg.maxuncommittedmemory);
    predicateParams.setArity(cfg.arity);
    predicateParams.setBounds(cfg.lowerbound, cfg.upperbound);
    predicateParams.setDensePostingListThreshold(cfg.densepostinglistthreshold);
    retval.setPredicateParams(predicateParams);
    auto match = convertMatch(cfg.match);
    auto dictionary = convert_dictionary(cfg.dictionary);
    // Only string attributes read the dictionary match setting; for the other types it has no effect,
    // and the attribute match setting is not used at all.
    if (bType.type() == BasicType::STRING) {
        dictionary = check_dictionary_match(cfg.name, dictionary, match);
    }
    retval.set_dictionary_config(dictionary);
    retval.set_match(match);
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
    case CfgDm::PRENORMALIZED_ANGULAR:
        dm = DistanceMetric::PrenormalizedAngular;
        break;
    case CfgDm::DOTPRODUCT:
        dm = DistanceMetric::Dotproduct;
        break;
    }
    retval.set_distance_metric(dm);
    if (cfg.index.hnsw.enabled) {
        retval.set_hnsw_index_params(HnswIndexParams(cfg.index.hnsw.maxlinkspernode,
                                                     cfg.index.hnsw.neighborstoexploreatinsert, dm,
                                                     cfg.index.hnsw.multithreadedindexing));
    }
    std::optional<QuantizationParams> quantization_params;
    if (cfg.quantization.bits > 0) {
        // For now, hardcode the randomization seed to make the quantization precision
        // reproducible across nodes and deployments.
        // We can make this configurable later if needed, but this will require persisting
        // the seed as part of the attribute headers for cross-checking, as using the wrong
        // seed for quantized operations yields completely bogus results.
        // The default Most Blessed Seed is the 64 MSBs of sha256(utf8_bytes("Vinsjan på kaia")).
        constexpr uint64_t quant_seed = 0xd4517d0bd7375213;
        // Quantization mode depends on the chosen distance metric. We prefer inner-product
        // optimized quantization for everything except Euclidean distance (which is the
        // default if no distance metric is set, meaning we prioritize vector reconstruction
        // precision over unbiased inner products).
        // TODO consider adding explicit schema config that can override this; people may use quantized
        //  vectors without a configured distance metric, but then use dot product etc for ranking...
        const auto quant_mode = (dm == DistanceMetric::Euclidean)
                                    ? QuantizationParams::QuantizationMode::MSE
                                    : QuantizationParams::QuantizationMode::InnerProduct;
        quantization_params = QuantizationParams(quant_seed, quant_mode, cfg.quantization.bits);
    }
    if (retval.basicType().type() == BasicType::Type::TENSOR) {
        if (!cfg.tensortype.empty()) {
            if (!quantization_params) {
                retval.setTensorType(ValueType::from_spec(cfg.tensortype));
            } else {
                retval.set_tensor_type_with_quantization(ValueType::from_spec(cfg.tensortype), *quantization_params);
            }
        } else {
            retval.setTensorType(ValueType::double_type());
        }
    }
    return retval;
}

} // namespace search::attribute
