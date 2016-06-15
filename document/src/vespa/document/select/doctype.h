// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::DocType
 * @ingroup select
 *
 * @brief Class matching whether a document is of given type or not.
 *
 * @author H�kon Humberset
 * @date 2005-06-07
 * @version $Id$
 */

#pragma once

#include <vespa/document/datatype/documenttype.h>
#include "node.h"

namespace document {
namespace select {

class DocType : public Node
{
private:
    vespalib::string _doctype;

public:
    DocType(const vespalib::stringref& doctype);

    virtual ResultList contains(const Context&) const;
    virtual ResultList trace(const Context&, std::ostream& trace) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
    virtual void visit(Visitor& v) const;

    Node::UP clone() const { return wrapParens(new DocType(_doctype)); }

};

} // select
} // document

