// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_spec.h"
#include "attribute_initializer_result.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/common/serialnum.h>
#include <optional>

namespace search::attribute { class AttributeHeader; }
namespace vespalib { class Executor; }

namespace proton {

class AttributeDirectory;
struct IAttributeFactory;

/**
 * Class used by an attribute manager to initialize and load attribute vectors from disk.
 */
class AttributeInitializer
{
public:
    using UP = std::unique_ptr<AttributeInitializer>;

private:
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    std::shared_ptr<AttributeDirectory> _attrDir;
    const vespalib::string          _documentSubDbName;
    const AttributeSpec             _spec;
    const std::optional<uint64_t>   _currentSerialNum;
    const IAttributeFactory        &_factory;
    vespalib::Executor             &_shared_executor;
    std::unique_ptr<const search::attribute::AttributeHeader> _header;
    bool                            _header_ok;

    void readHeader();

    AttributeVectorSP tryLoadAttribute() const;

    bool loadAttribute(const AttributeVectorSP &attr, search::SerialNum serialNum) const;

    void setupEmptyAttribute(AttributeVectorSP &attr, search::SerialNum serialNum,
                             const search::attribute::AttributeHeader &header) const;

    AttributeVectorSP createAndSetupEmptyAttribute() const;

public:
    AttributeInitializer(const std::shared_ptr<AttributeDirectory> &attrDir, const vespalib::string &documentSubDbName,
                         AttributeSpec && spec, std::optional<uint64_t> currentSerialNum, const IAttributeFactory &factory,
                         vespalib::Executor& shared_executor);
    ~AttributeInitializer();

    AttributeInitializerResult init() const;
    const std::optional<uint64_t>& getCurrentSerialNum() const noexcept { return _currentSerialNum; }
    size_t get_transient_memory_usage() const;
};

} // namespace proton

