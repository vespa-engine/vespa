// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initializer.h"
#include "attributedisklayout.h"
#include "attribute_directory.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_initializer");

using search::attribute::BasicType;
using search::attribute::Config;
using search::AttributeVector;
using search::IndexMetaInfo;

namespace proton {

typedef AttributeInitializer::AttributeHeader AttributeHeader;

namespace {

const vespalib::string dataTypeTag = "datatype";
const vespalib::string collectionTypeTag = "collectiontype";
const vespalib::string createSerialNumTag = "createSerialNum";
const vespalib::string tensorTypeTag = "tensortype";
const vespalib::string predicateArityTag = "predicate.arity";
const vespalib::string predicateLowerBoundTag = "predicate.lower_bound";
const vespalib::string predicateUpperBoundTag = "predicate.upper_bound";

vespalib::string
extraPredicateType(const search::attribute::PersistentPredicateParams &params)
{
    vespalib::asciistream os;
    os << "arity=" << params.arity();
    os << ",lower_bound=" << params.lower_bound();
    os << ",upper_bound=" << params.upper_bound();
    return os.str();
}

vespalib::string
extraType(const Config &cfg)
{
    if (cfg.basicType().type() == BasicType::TENSOR) {
        return cfg.tensorType().to_spec();
    }
    if (cfg.basicType().type() == BasicType::PREDICATE) {
        return extraPredicateType(cfg.predicateParams());
    }
    return "";
}

vespalib::string
extraType(const AttributeHeader &header)
{
    if (header._btString == "tensor") {
        return header._ttString;
    }
    if (header._btString == "predicate") {
        if (header._predicateParamsSet) {
            return extraPredicateType(header._predicateParams);
        }
    }
    return "";
}

uint64_t
extractCreateSerialNum(const vespalib::GenericHeader &header)
{
    if (header.hasTag(createSerialNumTag)) {
        return header.getTag(createSerialNumTag).asInteger();
    } else {
        return 0u;
    }
}

bool
extractHeaderTypeOK(const vespalib::GenericHeader &header, const Config &cfg)
{
    if (!header.hasTag(dataTypeTag) || !header.hasTag(collectionTypeTag)) {
        return false;
    }
    if ((header.getTag(dataTypeTag).asString() != cfg.basicType().asString()) ||
        (header.getTag(collectionTypeTag).asString() != cfg.collectionType().asString())) {
        return false;
    }
    if (cfg.basicType().type() == BasicType::TENSOR) {
        if (!header.hasTag(tensorTypeTag)) {
            return false;
        }
        if (header.getTag(tensorTypeTag).asString() != cfg.tensorType().to_spec()) {
            return false;
        }
    }
    if (cfg.basicType().type() == BasicType::PREDICATE) {
        if (header.hasTag(predicateArityTag) && header.hasTag(predicateLowerBoundTag) && header.hasTag(predicateUpperBoundTag)) {
            const auto &params = cfg.predicateParams();
            if ((header.getTag(predicateArityTag).asInteger() != params.arity()) ||
                (header.getTag(predicateLowerBoundTag).asInteger() != params.lower_bound()) ||
                (header.getTag(predicateUpperBoundTag).asInteger() != params.upper_bound())) {
                return false;
            }
        }
    }
    return true;
}

AttributeHeader
extractHeader(const AttributeVector::SP &attr,
              const vespalib::string &attrFileName)
{

    auto df = search::FileUtil::openFile(attrFileName + ".dat");
    vespalib::FileHeader datHeader;
    datHeader.readFile(*df);
    AttributeHeader retval;
    retval._createSerialNum = extractCreateSerialNum(datHeader);
    retval._headerTypeOK = extractHeaderTypeOK(datHeader, attr->getConfig());
    if (datHeader.hasTag(dataTypeTag)) {
        retval._btString = datHeader.getTag(dataTypeTag).asString();
    }
    if (datHeader.hasTag(collectionTypeTag)) {
        retval._ctString = datHeader.getTag(collectionTypeTag).asString();
    }
    if (datHeader.hasTag(tensorTypeTag)) {
        retval._ttString = datHeader.getTag(tensorTypeTag).asString();
    }
    if (datHeader.hasTag(predicateArityTag)) {
        retval._predicateParamsSet = true;
        retval._predicateParams.setArity(datHeader.getTag(predicateArityTag).asInteger());
    }
    if (datHeader.hasTag(predicateLowerBoundTag)) {
        retval._predicateParamsSet = true;
        retval._predicateParams.setBounds(datHeader.getTag(predicateLowerBoundTag).asInteger(), retval._predicateParams.upper_bound());
    }
    if (datHeader.hasTag(predicateUpperBoundTag)) {
        retval._predicateParamsSet = true;
        retval._predicateParams.setBounds(retval._predicateParams.lower_bound(), datHeader.getTag(predicateUpperBoundTag).asInteger());
    }
    return retval;
}

void
logAttributeTooNew(const AttributeVector::SP &attr,
                   const AttributeHeader &header,
                   uint64_t serialNum)
{
    LOG(info, "Attribute vector '%s' is too new (%" PRIu64 " > %" PRIu64 ")",
            attr->getBaseFileName().c_str(),
            header._createSerialNum,
            serialNum);
}

void
logAttributeWrongType(const AttributeVector::SP &attr,
                      const AttributeHeader &header)
{
    const Config &cfg(attr->getConfig());
    vespalib::string extraCfgType = extraType(cfg);
    vespalib::string extraHeaderType = extraType(header);
    LOG(info, "Attribute vector '%s' is of wrong type (expected %s/%s/%s, got %s/%s/%s)",
            attr->getBaseFileName().c_str(),
            cfg.basicType().asString(),
            cfg.collectionType().asString(),
            extraCfgType.c_str(),
            header._btString.c_str(),
            header._ctString.c_str(),
            extraHeaderType.c_str());
}

}

AttributeInitializer::AttributeHeader::AttributeHeader()
    : _createSerialNum(0),
      _headerTypeOK(false),
      _predicateParamsSet(false),
      _btString("unknown"),
      _ctString("unknown"),
      _ttString("unknown"),
      _predicateParams()
{
}

AttributeInitializer::AttributeHeader::~AttributeHeader() {}

AttributeVector::SP
AttributeInitializer::tryLoadAttribute() const
{
    search::SerialNum serialNum = _attrDir->getFlushedSerialNum();
    vespalib::string attrFileName = _attrDir->getAttributeFileName(serialNum);
    AttributeVector::SP attr = _factory.create(attrFileName, _cfg);
    if (serialNum != 0) {
        AttributeHeader header = extractHeader(attr, attrFileName);
        if (header._createSerialNum > _currentSerialNum || !header._headerTypeOK) {
            setupEmptyAttribute(attr, serialNum, header);
            return attr;
        }
        if (!loadAttribute(attr, serialNum)) {
            return AttributeVector::SP();
        }
    } else {
        _factory.setupEmpty(attr, _currentSerialNum);
    }
    return attr;
}

bool
AttributeInitializer::loadAttribute(const AttributeVector::SP &attr,
                                    search::SerialNum serialNum) const
{
    assert(attr->hasLoadData());
    fastos::TimeStamp startTime = fastos::ClockSystem::now();
    EventLogger::loadAttributeStart(_documentSubDbName, attr->getName());
    if (!attr->load()) {
        LOG(warning, "Could not load attribute vector '%s' from disk. "
                "Returning empty attribute vector",
                attr->getBaseFileName().c_str());
        return false;
    } else {
        attr->commit(serialNum, serialNum);
        fastos::TimeStamp endTime = fastos::ClockSystem::now();
        int64_t elapsedTimeMs = (endTime - startTime).ms();
        EventLogger::loadAttributeComplete(_documentSubDbName, attr->getName(), elapsedTimeMs);
    }
    return true;
}

void
AttributeInitializer::setupEmptyAttribute(AttributeVector::SP &attr,
                                          search::SerialNum serialNum,
                                          const AttributeHeader &header) const
{
    if (header._createSerialNum > _currentSerialNum) {
        logAttributeTooNew(attr, header, _currentSerialNum);
    }
    if (!header._headerTypeOK) {
        logAttributeWrongType(attr, header);
    }
    LOG(info, "Returning empty attribute vector for '%s'",
            attr->getBaseFileName().c_str());
    _factory.setupEmpty(attr, _currentSerialNum);
    attr->commit(serialNum, serialNum);
}

AttributeVector::SP
AttributeInitializer::createAndSetupEmptyAttribute() const
{
    vespalib::string attrFileName = _attrDir->getAttributeFileName(0);
    AttributeVector::SP attr = _factory.create(attrFileName, _cfg);
    _factory.setupEmpty(attr, _currentSerialNum);
    return attr;
}

AttributeInitializer::AttributeInitializer(const std::shared_ptr<AttributeDirectory> &attrDir,
                                           const vespalib::string &documentSubDbName,
                                           const search::attribute::Config &cfg,
                                           uint64_t currentSerialNum,
                                           const IAttributeFactory &factory)
    : _attrDir(attrDir),
      _documentSubDbName(documentSubDbName),
      _cfg(cfg),
      _currentSerialNum(currentSerialNum),
      _factory(factory)
{
}

AttributeInitializer::~AttributeInitializer() {}

search::AttributeVector::SP
AttributeInitializer::init() const
{
    if (!_attrDir->empty()) {
        return tryLoadAttribute();
    } else {
        return createAndSetupEmptyAttribute();
    }
}

} // namespace proton
