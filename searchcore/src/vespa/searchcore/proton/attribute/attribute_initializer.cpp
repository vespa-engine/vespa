// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initializer.h"
#include "attributedisklayout.h"
#include "attribute_directory.h"
#include "i_attribute_factory.h"
#include "attribute_transient_memory_calculator.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchcommon/attribute/persistent_predicate_params.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/searchlib/attribute/attribute_header.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_initializer");

using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::AttributeVector;
using search::IndexMetaInfo;
using search::CommitParam;

namespace proton {

using search::attribute::AttributeHeader;

namespace {

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
    if (cfg.basicType().type() == BasicType::Type::TENSOR) {
        return cfg.tensorType().to_spec();
    }
    if (cfg.basicType().type() == BasicType::Type::PREDICATE) {
        return extraPredicateType(cfg.predicateParams());
    }
    return "";
}

vespalib::string
extraType(const AttributeHeader &header)
{
    if (header.getBasicType().type() == BasicType::Type::TENSOR) {
        return header.getTensorType().to_spec();
    }
    if (header.getBasicType().type() == BasicType::Type::PREDICATE) {
        return extraPredicateType(header.getPredicateParams());
    }
    return "";
}

vespalib::string
collectionTypeString(const CollectionType &type, bool detailed)
{
    vespalib::asciistream os;
    os << type.asString();
    if (type.type() == CollectionType::Type::WSET && detailed) {
        os << "(";
        bool first = true;
        if (type.createIfNonExistant()) {
            os << "add";
            first = false;
        }
        if (type.removeIfZero()) {
            if (!first) {
                os << ",";
            }
            os << "remove";
        }
        os << ")";
    }
    return os.str();
}

bool
headerTypeOK(const AttributeHeader &header, const Config &cfg)
{
    if ((header.getBasicType().type() != cfg.basicType().type()) ||
        (header.getCollectionType().type() != cfg.collectionType().type())) {
        return false;
    }
    if (header.getCollectionTypeParamsSet() &&
        (header.getCollectionType() != cfg.collectionType())) {
        return false;
    }
    if (cfg.basicType().type() == BasicType::Type::TENSOR) {
        if (header.getTensorType() != cfg.tensorType()) {
            return false;
        }
    }
    if (cfg.basicType().type() == BasicType::PREDICATE) {
        if (header.getPredicateParamsSet()) {
            if (!(header.getPredicateParams() == cfg.predicateParams())) {
                return false;
            }
        }
    }
    return true;
}

AttributeHeader
extractHeader(const vespalib::string &attrFileName)
{
    auto df = search::FileUtil::openFile(attrFileName + ".dat");
    vespalib::FileHeader datHeader;
    datHeader.readFile(*df);
    return AttributeHeader::extractTags(datHeader);
}

void
logAttributeTooNew(const AttributeHeader &header, uint64_t serialNum)
{
    LOG(info, "Attribute vector '%s' is too new (%" PRIu64 " > %" PRIu64 ")",
        header.getFileName().c_str(), header.getCreateSerialNum(), serialNum);
}

void
logAttributeTooOld(const AttributeHeader &header, uint64_t flushedSerialNum, uint64_t serialNum)
{
    LOG(info, "Attribute vector '%s' is too old (%" PRIu64 " < %" PRIu64 ")",
        header.getFileName().c_str(), flushedSerialNum, serialNum);
}

void
logAttributeWrongType(const AttributeVector::SP &attr, const AttributeHeader &header)
{
    const Config &cfg(attr->getConfig());
    vespalib::string extraCfgType = extraType(cfg);
    vespalib::string extraHeaderType = extraType(header);
    vespalib::string cfgCollStr = collectionTypeString(cfg.collectionType(), true);
    vespalib::string headerCollStr = collectionTypeString(header.getCollectionType(), header.getCollectionTypeParamsSet());
    LOG(info, "Attribute vector '%s' is of wrong type (expected %s/%s/%s, got %s/%s/%s)",
        header.getFileName().c_str(), cfg.basicType().asString(), cfgCollStr.c_str(), extraCfgType.c_str(),
        header.getBasicType().asString(), headerCollStr.c_str(), extraHeaderType.c_str());
}

}

void
AttributeInitializer::readHeader()
{
    if (!_attrDir->empty()) {
        search::SerialNum serialNum = _attrDir->getFlushedSerialNum();
        vespalib::string attrFileName = _attrDir->getAttributeFileName(serialNum);
        if (serialNum != 0) {
            _header = std::make_unique<const AttributeHeader>(extractHeader(attrFileName));
            if (_header->getCreateSerialNum() <= _currentSerialNum && headerTypeOK(*_header, _spec.getConfig()) && (serialNum >= _currentSerialNum)) {
                _header_ok = true;
            }
        }
    }
}

AttributeVector::SP
AttributeInitializer::tryLoadAttribute() const
{
    search::SerialNum serialNum = _attrDir->getFlushedSerialNum();
    vespalib::string attrFileName = _attrDir->getAttributeFileName(serialNum);
    AttributeVector::SP attr = _factory.create(attrFileName, _spec.getConfig());
    if (serialNum != 0 && _header) {
        if (!_header_ok) {
            setupEmptyAttribute(attr, serialNum, *_header);
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
AttributeInitializer::loadAttribute(const AttributeVectorSP &attr,
                                    search::SerialNum serialNum) const
{
    assert(attr->hasLoadData());
    vespalib::Timer timer;
    EventLogger::loadAttributeStart(_documentSubDbName, attr->getName());
    if (!attr->load()) {
        LOG(warning, "Could not load attribute vector '%s' from disk. Returning empty attribute vector",
            attr->getBaseFileName().c_str());
        return false;
    } else {
        attr->commit(CommitParam(serialNum));
        EventLogger::loadAttributeComplete(_documentSubDbName, attr->getName(), timer.elapsed());
    }
    return true;
}

void
AttributeInitializer::setupEmptyAttribute(AttributeVectorSP &attr, search::SerialNum serialNum,
                                          const AttributeHeader &header) const
{
    if (header.getCreateSerialNum() > _currentSerialNum) {
        logAttributeTooNew(header, _currentSerialNum);
    }
    if (serialNum < _currentSerialNum) {
        logAttributeTooOld(header, serialNum, _currentSerialNum);
    }
    if (!headerTypeOK(header, attr->getConfig())) {
        logAttributeWrongType(attr, header);
    }
    LOG(info, "Returning empty attribute vector for '%s'", attr->getBaseFileName().c_str());
    _factory.setupEmpty(attr, _currentSerialNum);
    attr->commit(CommitParam(serialNum));
}

AttributeVector::SP
AttributeInitializer::createAndSetupEmptyAttribute() const
{
    AttributeVector::SP attr = _factory.create(_attrDir->getAttrName(), _spec.getConfig());
    _factory.setupEmpty(attr, _currentSerialNum);
    return attr;
}

AttributeInitializer::AttributeInitializer(const std::shared_ptr<AttributeDirectory> &attrDir,
                                           const vespalib::string &documentSubDbName,
                                           const AttributeSpec &spec,
                                           uint64_t currentSerialNum,
                                           const IAttributeFactory &factory)
    : _attrDir(attrDir),
      _documentSubDbName(documentSubDbName),
      _spec(spec),
      _currentSerialNum(currentSerialNum),
      _factory(factory),
      _header(),
      _header_ok(false)
{
    readHeader();
}

AttributeInitializer::~AttributeInitializer() = default;

AttributeInitializerResult
AttributeInitializer::init() const
{
    if (!_attrDir->empty()) {
        return AttributeInitializerResult(tryLoadAttribute());
    } else {
        return AttributeInitializerResult(createAndSetupEmptyAttribute());
    }
}

size_t
AttributeInitializer::get_transient_memory_usage() const
{
    if (_header_ok) {
        AttributeTransientMemoryCalculator get_transient_memory_usage;
        return get_transient_memory_usage(*_header, _spec.getConfig());
    }
    return 0u;
}

} // namespace proton
