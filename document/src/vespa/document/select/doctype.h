// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::DocType
 * @ingroup select
 *
 * @brief Class matching whether a document is of given type or not.
 *
 * @author H�kon Humberset
 */

#pragma once

#include "node.h"

namespace document::select {

class DocType : public Node
{
private:
    vespalib::string _doctype;

public:
    DocType(vespalib::stringref doctype);

    ResultList contains(const Context&) const override;
    ResultList trace(const Context&, std::ostream& trace) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& v) const override;

    Node::UP clone() const override { return wrapParens(new DocType(_doctype)); }

};

}
