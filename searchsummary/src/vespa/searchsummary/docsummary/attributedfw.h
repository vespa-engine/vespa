// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsumfieldwriter.h"


namespace search { class MatchingElementsFields; }
namespace search::attribute { class IAttributeVector; }

namespace search::docsummary {

/**
 * Factory to create an IDocsumFieldWriter to write an attribute vector to slime.
 */
class AttributeDFWFactory {
public:
    static std::unique_ptr<IDocsumFieldWriter> create(IAttributeManager& attr_mgr,
                                                      const vespalib::string& attr_name,
                                                      bool filter_elements = false,
                                                      std::shared_ptr<MatchingElementsFields> matching_elems_fields
                                                      = std::shared_ptr<MatchingElementsFields>());
};

class AttrDFW : public ISimpleDFW
{
private:
    vespalib::string _attrName;
protected:
    const attribute::IAttributeVector& get_attribute(const GetDocsumsState& s) const;
    const vespalib::string & getAttributeName() const override { return _attrName; }
public:
    AttrDFW(const vespalib::string & attrName);
    bool IsGenerated() const override { return true; }
};

}

