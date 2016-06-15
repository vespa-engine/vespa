// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::InvalidConstant
 * @ingroup select
 *
 * @brief Class describing an invalid constant in the select tree.
 *
 * @author H�kon Humberset
 * @date 2005-06-07
 * @version $Id$
 */

#pragma once

#include "node.h"

namespace document {
namespace select {

class InvalidConstant : public Node
{
public:
    explicit InvalidConstant(const vespalib::stringref &value);

    virtual ResultList contains(const Context&) const
        { return ResultList(Result::Invalid); }
    virtual ResultList trace(const Context&, std::ostream& trace) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
    virtual void visit(Visitor& v) const;

    Node::UP clone() const { return wrapParens(new InvalidConstant(_name)); }

};

} // select
} // document

