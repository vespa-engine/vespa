// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchcommon/attribute/persistent_predicate_params.h>

namespace search {
namespace attribute { class AttributeHeader; }
class AttributeVector;
}

namespace proton {

class AttributeDirectory;
class IAttributeFactory;

/**
 * Class used by an attribute manager to initialize and load attribute vectors from disk.
 */
class AttributeInitializer
{
public:
    typedef std::unique_ptr<AttributeInitializer> UP;

private:
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    std::shared_ptr<AttributeDirectory> _attrDir;
    const vespalib::string          _documentSubDbName;
    const search::attribute::Config _cfg;
    const uint64_t                  _currentSerialNum;
    const IAttributeFactory        &_factory;

    AttributeVectorSP tryLoadAttribute() const;

    bool loadAttribute(const AttributeVectorSP &attr,
                       search::SerialNum serialNum) const;

    void setupEmptyAttribute(AttributeVectorSP &attr,
                             search::SerialNum serialNum,
                             const search::attribute::AttributeHeader &header) const;

    AttributeVectorSP createAndSetupEmptyAttribute() const;

public:
    AttributeInitializer(const std::shared_ptr<AttributeDirectory> &attrDir,
                         const vespalib::string &documentSubDbName,
                         const search::attribute::Config &cfg,
                         uint64_t currentSerialNum,
                         const IAttributeFactory &factory);
    ~AttributeInitializer();

    AttributeVectorSP init() const;
    uint64_t getCurrentSerialNum() const { return _currentSerialNum; }
};

} // namespace proton

