// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_header.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/data/databuffer.h>

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
      _numDocs(0),
      _uniqueValueCount(0),
      _totalValueCount(0),
      _createSerialNum(0u),
      _version(0)
{
}

AttributeHeader::AttributeHeader(const vespalib::string &fileName, attribute::BasicType basicType,
                                 attribute::CollectionType collectionType, const vespalib::eval::ValueType &tensorType,
                                 bool enumerated, const attribute::PersistentPredicateParams &predicateParams,
                                 uint32_t numDocs, [[maybe_unused]] uint32_t fixedWidth, uint64_t uniqueValueCount,
                                 uint64_t totalValueCount, uint64_t createSerialNum, uint32_t version)
    : _fileName(fileName),
      _basicType(basicType),
      _collectionType(collectionType),
      _tensorType(tensorType),
      _enumerated(enumerated),
      _collectionTypeParamsSet(false),
      _predicateParamsSet(false),
      _predicateParams(predicateParams),
      _numDocs(numDocs),
      _uniqueValueCount(uniqueValueCount),
      _totalValueCount(totalValueCount),
      _createSerialNum(createSerialNum),
      _version(version)
{
}

AttributeHeader::~AttributeHeader() = default;

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
    if (header.hasTag(versionTag)) {
        _version = header.getTag(versionTag).asInteger();
    }
}

AttributeHeader
AttributeHeader::extractTags(const vespalib::GenericHeader &header)
{
    AttributeHeader result;
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
    header.putTag(Tag("uniqueValueCount", _uniqueValueCount));
    header.putTag(Tag("totalValueCount", _totalValueCount));
    header.putTag(Tag("docIdLimit", _numDocs));
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("fileBitSize", 0));
    header.putTag(Tag(versionTag, _version));
    if (_enumerated) {
        header.putTag(Tag("enumerated", 1));
    }
    if (_createSerialNum != 0u) {
        header.putTag(Tag(createSerialNumTag, _createSerialNum));
    }
    if (_basicType.type() == attribute::BasicType::Type::TENSOR) {
        header.putTag(Tag(tensorTypeTag, _tensorType.to_spec()));;
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
