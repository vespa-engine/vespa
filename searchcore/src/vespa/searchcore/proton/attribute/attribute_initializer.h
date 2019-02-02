// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_spec.h"
#include "attribute_initializer_result.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchcommon/attribute/persistent_predicate_params.h>

namespace search::attribute { class AttributeHeader; }

namespace proton {

class AttributeDirectory;
struct IAttributeFactory;

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
    const AttributeSpec             _spec;
    const uint64_t                  _currentSerialNum;
    const IAttributeFactory        &_factory;

    AttributeVectorSP tryLoadAttribute() const;

    bool loadAttribute(const AttributeVectorSP &attr, search::SerialNum serialNum) const;

    void setupEmptyAttribute(AttributeVectorSP &attr, search::SerialNum serialNum,
                             const search::attribute::AttributeHeader &header) const;

    AttributeVectorSP createAndSetupEmptyAttribute() const;

public:
    AttributeInitializer(const std::shared_ptr<AttributeDirectory> &attrDir, const vespalib::string &documentSubDbName,
                         const AttributeSpec &spec, uint64_t currentSerialNum, const IAttributeFactory &factory);
    ~AttributeInitializer();

    AttributeInitializerResult init() const;
    uint64_t getCurrentSerialNum() const { return _currentSerialNum; }
};

} // namespace proton

