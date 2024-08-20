// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simple_dfw.h"
#include <memory>

namespace search {
class IAttributeManager;
class MatchingElementsFields;
}
namespace search::attribute { class IAttributeVector; }

namespace search::docsummary {

/**
 * Factory to create an DocsumFieldWriter to write an attribute vector to slime.
 */
class AttributeDFWFactory {
public:
    static std::unique_ptr<DocsumFieldWriter> create(const IAttributeManager& attr_mgr,
                                                     const std::string& attr_name,
                                                     bool filter_elements = false,
                                                     std::shared_ptr<MatchingElementsFields> matching_elems_fields = std::shared_ptr<MatchingElementsFields>());
};

class AttrDFW : public SimpleDFW
{
private:
    std::string _attrName;
protected:
    const attribute::IAttributeVector& get_attribute(const GetDocsumsState& s) const;
    const std::string & getAttributeName() const override { return _attrName; }
public:
    explicit AttrDFW(const std::string & attrName);
    bool isGenerated() const override { return true; }
};

}

