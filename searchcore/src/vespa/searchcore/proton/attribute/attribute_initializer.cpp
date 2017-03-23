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

using search::attribute::Config;
using search::AttributeVector;
using search::IndexMetaInfo;

namespace proton {

typedef AttributeInitializer::AttributeHeader AttributeHeader;

namespace {

const vespalib::string dataTypeTag = "datatype";
const vespalib::string collectionTypeTag = "collectiontype";
const vespalib::string createSerialNumTag = "createSerialNum";

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
    return header.hasTag(dataTypeTag) &&
        header.hasTag(collectionTypeTag) &&
        header.getTag(dataTypeTag).asString() ==
        cfg.basicType().asString() &&
        header.getTag(collectionTypeTag).asString() ==
        cfg.collectionType().asString();
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
    LOG(info, "Attribute vector '%s' is of wrong type (expected %s/%s, got %s/%s)",
            attr->getBaseFileName().c_str(),
            cfg.basicType().asString(),
            cfg.collectionType().asString(),
            header._btString.c_str(),
            header._ctString.c_str());
}

}

AttributeInitializer::AttributeHeader::AttributeHeader()
    : _createSerialNum(0),
      _headerTypeOK(false),
      _btString("unknown"),
      _ctString("unknown")
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
