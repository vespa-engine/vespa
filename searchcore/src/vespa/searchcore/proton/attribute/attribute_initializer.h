// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_initializer_result.h"
#include "attribute_spec.h"

#include <vespa/searchcore/proton/common/doc_type_and_subdb_name.h>
#include <vespa/searchlib/common/serialnum.h>

#include <optional>
#include <string>

namespace search::attribute {
class AttributeHeader;
class AttributeInitializationStatus;
} // namespace search::attribute
namespace vespalib {
class Executor;
}

namespace proton::initializer {
class LoadMemoryUsage;
}

namespace proton {

class AttributeDirectory;
struct IAttributeFactory;

/**
 * Class used by an attribute manager to initialize and load attribute vectors from disk.
 */
class AttributeInitializer {
public:
    using UP = std::unique_ptr<AttributeInitializer>;

private:
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    std::shared_ptr<AttributeDirectory>                               _attrDir;
    const std::shared_ptr<const DocTypeAndSubDbName>                  _doc_type_and_subdb_name;
    const AttributeSpec                                               _spec;
    const std::optional<uint64_t>                                     _currentSerialNum;
    const IAttributeFactory&                                          _factory;
    vespalib::Executor&                                               _shared_executor;
    std::unique_ptr<const search::attribute::AttributeHeader>         _header;
    bool                                                              _header_ok;
    std::shared_ptr<search::attribute::AttributeInitializationStatus> _initialization_status;

    void readHeader();

    AttributeVectorSP tryLoadAttribute() const;

    bool loadAttribute(const AttributeVectorSP& attr, search::SerialNum serialNum) const;

    void setupEmptyAttribute(AttributeVectorSP& attr, search::SerialNum serialNum,
                             const search::attribute::AttributeHeader& header) const;

    AttributeVectorSP createAndSetupEmptyAttribute() const;

public:
    AttributeInitializer(const std::shared_ptr<AttributeDirectory>& attrDir,
                         std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in, AttributeSpec&& spec,
                         std::optional<uint64_t> currentSerialNum, const IAttributeFactory& factory,
                         vespalib::Executor& shared_executor);
    ~AttributeInitializer();

    AttributeInitializerResult init() const;
    const std::optional<uint64_t>& getCurrentSerialNum() const noexcept { return _currentSerialNum; }
    [[nodiscard]] initializer::LoadMemoryUsage get_load_memory_usage() const noexcept;
    std::shared_ptr<search::attribute::AttributeInitializationStatus> get_attribute_initialization_status() const {
        return _initialization_status;
    }
    [[nodiscard]] const std::shared_ptr<AttributeDirectory>& attr_dir() const noexcept { return _attrDir; }
    [[nodiscard]] const DocTypeName& doc_type_name() const noexcept {
        return _doc_type_and_subdb_name->doc_type_name();
    }
    [[nodiscard]] const std::string& subdb_name() const noexcept { return _doc_type_and_subdb_name->subdb_name(); }
};

} // namespace proton
