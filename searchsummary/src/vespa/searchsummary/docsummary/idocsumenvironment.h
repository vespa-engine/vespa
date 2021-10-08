// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/iattributemanager.h>

namespace juniper { class Juniper; }
namespace search::docsummary {

/**
 * Abstract view of information available to rewriters for generating docsum fields.
 **/
class IDocsumEnvironment {
public:
    virtual search::IAttributeManager * getAttributeManager() = 0;
    virtual vespalib::string lookupIndex(const vespalib::string & s) const = 0;
    virtual juniper::Juniper * getJuniper() = 0;
    virtual ~IDocsumEnvironment() {}
};

}
