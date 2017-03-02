// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_factory.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton {

/**
 * Class used by an attribute manager to initialize and load attribute vectors from disk.
 */
class AttributeInitializer
{
public:
    typedef std::unique_ptr<AttributeInitializer> UP;

    struct AttributeHeader
    {
        uint64_t _createSerialNum;
        bool _headerTypeOK;
        vespalib::string _btString;
        vespalib::string _ctString;
        AttributeHeader();
        ~AttributeHeader();
    };

private:
    const vespalib::string          _baseDir;
    const vespalib::string          _documentSubDbName;
    const vespalib::string          _attrName;
    const search::attribute::Config _cfg;
    const uint64_t                  _currentSerialNum;
    const IAttributeFactory        &_factory;

    search::AttributeVector::SP tryLoadAttribute(const search::IndexMetaInfo &info) const;

    bool loadAttribute(const search::AttributeVector::SP &attr,
                       const search::IndexMetaInfo::Snapshot &snap) const;

    void setupEmptyAttribute(search::AttributeVector::SP &attr,
                             const search::IndexMetaInfo::Snapshot &snap,
                             const AttributeHeader &header) const;

    search::AttributeVector::SP createAndSetupEmptyAttribute(search::IndexMetaInfo &info) const;

public:
    AttributeInitializer(const vespalib::string &baseDir,
                         const vespalib::string &documentSubDbName,
                         const vespalib::string &attrName,
                         const search::attribute::Config &cfg,
                         uint64_t currentSerialNum,
                         const IAttributeFactory &factory);
    ~AttributeInitializer();

    search::AttributeVector::SP init() const;
    uint64_t getCurrentSerialNum() const { return _currentSerialNum; }
};

} // namespace proton

