// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/node.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace document {
class DocumentTypeRepo;

class DocumentCalculator {
public:
    typedef vespalib::hash_map<vespalib::string, double> VariableMap;

    DocumentCalculator(const DocumentTypeRepo& repo,
                       const vespalib::string& expression);
    double evaluate(const Document& doc, VariableMap& variables);

private:
    std::unique_ptr<select::Node> _selectionNode;
};

}

