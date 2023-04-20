// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_header.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/exceptions.h>

namespace search::attribute {

namespace {

const vespalib::string versionTag = "version";
const vespalib::string dataTypeTag = "datatype";
const vespalib::string collectionTypeTag = "collectiontype";
const vespalib::string createIfNonExistentTag = "collectiontype.createIfNonExistent";
const vespalib::string removeIfZeroTag = "collectiontype.removeIfZero";
const vespalib::string createSerialNumTag = "createSerialNum";
const vespalib::string tensorTypeTag = "tensortype";
const vespalib::string predicateArityTag = "predicate.arity";
const vespalib::string predicateLowerBoundTag = "predicate.lower_bound";
const vespalib::string predicateUpperBoundTag = "predicate.upper_bound";
const vespalib::string nearest_neighbor_index_tag = "nearest_neighbor_index";
const vespalib::string hnsw_index_value = "hnsw";
const vespalib::string hnsw_max_links_tag = "hnsw.max_links_per_node";
const vespalib::string hnsw_neighbors_to_explore_tag = "hnsw.neighbors_to_explore_at_insert";
const vespalib::string hnsw_distance_metric = "hnsw.distance_metric";
const vespalib::string euclidean = "euclidean";
const vespalib::string angular = "angular";
const vespalib::string geodegrees = "geodegrees";
const vespalib::string innerproduct = "innerproduct";
const vespalib::string prenormalized_angular = "prenormalized_angular";
const vespalib::string hamming = "hamming";
const vespalib::string doc_id_limit_tag = "docIdLimit";
const vespalib::string enumerated_tag = "enumerated";
const vespalib::string unique_value_count_tag = "uniqueValueCount";
const vespalib::string total_value_count_tag = "totalValueCount";

}

AttributeHeader::AttributeHeader()
    : AttributeHeader("")
{
}

AttributeHeader::AttributeHeader(const vespalib::string &fileName)
    : _fileName(fileName),
      _basicType(attribute::BasicType::Type::NONE),
      _collectionType(attribute::CollectionType::Type::SINGLE),
      _tensorType(vespalib::eval::ValueType::error_type()),
      _enumerated(false),
      _collectionTypeParamsSet(false),
      _predicateParamsSet(false),
      _predicateParams(),
      _hnsw_index_params(),
      _numDocs(0),
      _uniqueValueCount(0),
      _totalValueCount(0),
      _createSerialNum(0u),
      _version(0)
{
}

AttributeHeader::AttributeHeader(const vespalib::string &fileName,
                                 attribute::BasicType basicType,
                                 attribute::CollectionType collectionType,
                                 const vespalib::eval::ValueType &tensorType,
                                 bool enumerated,
                                 const attribute::PersistentPredicateParams &predicateParams,
                                 const std::optional<HnswIndexParams>& hnsw_index_params,
                                 uint32_t numDocs,
                                 uint64_t uniqueValueCount,
                                 uint64_t totalValueCount,
                                 uint64_t createSerialNum,
                                 uint32_t version)
    : _fileName(fileName),
      _basicType(basicType),
      _collectionType(collectionType),
      _tensorType(tensorType),
      _enumerated(enumerated),
      _collectionTypeParamsSet(false),
      _predicateParamsSet(false),
      _predicateParams(predicateParams),
      _hnsw_index_params(hnsw_index_params),
      _numDocs(numDocs),
      _uniqueValueCount(uniqueValueCount),
      _totalValueCount(totalValueCount),
      _createSerialNum(createSerialNum),
      _version(version)
{
}

AttributeHeader::~AttributeHeader() = default;

namespace {

vespalib::string
to_string(DistanceMetric metric)
{
    switch (metric) {
        case DistanceMetric::Euclidean: return euclidean;
        case DistanceMetric::Angular: return angular;
        case DistanceMetric::GeoDegrees: return geodegrees;
        case DistanceMetric::InnerProduct: return innerproduct;
        case DistanceMetric::Hamming: return hamming;
        case DistanceMetric::PrenormalizedAngular: return prenormalized_angular;
    }
    throw vespalib::IllegalArgumentException("Unknown distance metric " + std::to_string(static_cast<int>(metric)));
}

DistanceMetric
to_distance_metric(const vespalib::string& metric)
{
    if (metric == euclidean) {
        return DistanceMetric::Euclidean;
    } else if (metric == angular) {
        return DistanceMetric::Angular;
    } else if (metric == geodegrees) {
        return DistanceMetric::GeoDegrees;
    } else if (metric == innerproduct) {
        return DistanceMetric::InnerProduct;
    } else if (metric == prenormalized_angular) {
      return DistanceMetric::PrenormalizedAngular;
    } else if (metric == hamming) {
        return DistanceMetric::Hamming;
    } else {
        throw vespalib::IllegalStateException("Unknown distance metric '" + metric + "'");
    }
}

}

void
AttributeHeader::internalExtractTags(const vespalib::GenericHeader &header)
{
    if (header.hasTag(createSerialNumTag)) {
        _createSerialNum = header.getTag(createSerialNumTag).asInteger();
    }
    if (header.hasTag(dataTypeTag)) {
        _basicType = BasicType(header.getTag(dataTypeTag).asString());
    }
    if (header.hasTag(collectionTypeTag)) {
        _collectionType = CollectionType(header.getTag(collectionTypeTag).asString());
    }
    if (_collectionType.type() == attribute::CollectionType::WSET) {
        if (header.hasTag(createIfNonExistentTag)) {
            assert(header.hasTag(removeIfZeroTag));
            _collectionTypeParamsSet = true;
            _collectionType.createIfNonExistant(header.getTag(createIfNonExistentTag).asBool());
            _collectionType.removeIfZero(header.getTag(removeIfZeroTag).asBool());
        } else {
            assert(!header.hasTag(removeIfZeroTag));
        }
    }
    if (_basicType.type() == BasicType::Type::TENSOR) {
        assert(header.hasTag(tensorTypeTag));
        _tensorType = vespalib::eval::ValueType::from_spec(header.getTag(tensorTypeTag).asString());
        if (header.hasTag(hnsw_max_links_tag)) {
            assert(header.hasTag(hnsw_neighbors_to_explore_tag));
            assert(header.hasTag(hnsw_distance_metric));

            uint32_t max_links = header.getTag(hnsw_max_links_tag).asInteger();
            uint32_t neighbors_to_explore = header.getTag(hnsw_neighbors_to_explore_tag).asInteger();
            DistanceMetric distance_metric = to_distance_metric(header.getTag(hnsw_distance_metric).asString());
            _hnsw_index_params.emplace(max_links, neighbors_to_explore, distance_metric);
        }
    }
    if (_basicType.type() == BasicType::Type::PREDICATE) {
        if (header.hasTag(predicateArityTag)) {
            assert(header.hasTag(predicateLowerBoundTag));
            assert(header.hasTag(predicateUpperBoundTag));
            _predicateParamsSet = true;
            _predicateParams.setArity(header.getTag(predicateArityTag).asInteger());
            _predicateParams.setBounds(header.getTag(predicateLowerBoundTag).asInteger(),
                                       header.getTag(predicateUpperBoundTag).asInteger());
        } else {
            assert(!header.hasTag(predicateLowerBoundTag));
            assert(!header.hasTag(predicateUpperBoundTag));
        }
    }
    if (header.hasTag(doc_id_limit_tag)) {
        _numDocs = header.getTag(doc_id_limit_tag).asInteger();
    }
    if (header.hasTag(enumerated_tag)) {
        _enumerated = header.getTag(enumerated_tag).asInteger() != 0;
    }
    if (header.hasTag(total_value_count_tag)) {
        _totalValueCount = header.getTag(total_value_count_tag).asInteger();
    }
    if (header.hasTag(unique_value_count_tag)) {
        _uniqueValueCount = header.getTag(unique_value_count_tag).asInteger();
    }
    if (header.hasTag(versionTag)) {
        _version = header.getTag(versionTag).asInteger();
    }
}

AttributeHeader
AttributeHeader::extractTags(const vespalib::GenericHeader &header, const vespalib::string &file_name)
{
    AttributeHeader result(file_name);
    result.internalExtractTags(header);
    return result;
}

void
AttributeHeader::addTags(vespalib::GenericHeader &header) const
{
    using Tag = vespalib::GenericHeader::Tag;
    header.putTag(Tag(dataTypeTag, _basicType.asString()));
    header.putTag(Tag(collectionTypeTag, _collectionType.asString()));
    if (_collectionType.type() == attribute::CollectionType::WSET) {
        header.putTag(Tag(createIfNonExistentTag, _collectionType.createIfNonExistant()));
        header.putTag(Tag(removeIfZeroTag, _collectionType.removeIfZero()));
    }
    header.putTag(Tag(unique_value_count_tag, _uniqueValueCount));
    header.putTag(Tag(total_value_count_tag, _totalValueCount));
    header.putTag(Tag(doc_id_limit_tag, _numDocs));
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("fileBitSize", 0));
    header.putTag(Tag(versionTag, _version));
    if (_enumerated) {
        header.putTag(Tag(enumerated_tag, 1));
    }
    if (_createSerialNum != 0u) {
        header.putTag(Tag(createSerialNumTag, _createSerialNum));
    }
    if (_basicType.type() == attribute::BasicType::Type::TENSOR) {
        header.putTag(Tag(tensorTypeTag, _tensorType.to_spec()));;
        if (_hnsw_index_params.has_value()) {
            header.putTag(Tag(nearest_neighbor_index_tag, hnsw_index_value));
            const auto& params = *_hnsw_index_params;
            header.putTag(Tag(hnsw_max_links_tag, params.max_links_per_node()));
            header.putTag(Tag(hnsw_neighbors_to_explore_tag, params.neighbors_to_explore_at_insert()));
            header.putTag(Tag(hnsw_distance_metric, to_string(params.distance_metric())));
        }
    }
    if (_basicType.type() == attribute::BasicType::Type::PREDICATE) {
        const auto & params = _predicateParams;
        header.putTag(Tag(predicateArityTag, params.arity()));
        header.putTag(Tag(predicateLowerBoundTag, params.lower_bound()));
        header.putTag(Tag(predicateUpperBoundTag, params.upper_bound()));
    }
}

bool
AttributeHeader::hasMultiValue() const
{
    return _collectionType.isMultiValue();
}

bool
AttributeHeader::hasWeightedSetType() const
{
    return _collectionType.isWeightedSet();
}

}
