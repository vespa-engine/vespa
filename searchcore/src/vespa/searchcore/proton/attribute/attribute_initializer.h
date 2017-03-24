// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_factory.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

class AttributeDirectory;

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
        vespalib::string _ttString;
        AttributeHeader();
        ~AttributeHeader();
    };

private:
    std::shared_ptr<AttributeDirectory> _attrDir;
    const vespalib::string          _documentSubDbName;
    const search::attribute::Config _cfg;
    const uint64_t                  _currentSerialNum;
    const IAttributeFactory        &_factory;

    search::AttributeVector::SP tryLoadAttribute() const;

    bool loadAttribute(const search::AttributeVector::SP &attr,
                       search::SerialNum serialNum) const;

    void setupEmptyAttribute(search::AttributeVector::SP &attr,
                             search::SerialNum serialNum,
                             const AttributeHeader &header) const;

    search::AttributeVector::SP createAndSetupEmptyAttribute() const;

public:
    AttributeInitializer(const std::shared_ptr<AttributeDirectory> &attrDir,
                         const vespalib::string &documentSubDbName,
                         const search::attribute::Config &cfg,
                         uint64_t currentSerialNum,
                         const IAttributeFactory &factory);
    ~AttributeInitializer();

    search::AttributeVector::SP init() const;
    uint64_t getCurrentSerialNum() const { return _currentSerialNum; }
};

} // namespace proton

