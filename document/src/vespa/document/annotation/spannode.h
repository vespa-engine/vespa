// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace document {
struct SpanTreeVisitor;

struct SpanNode {
    using UP = std::unique_ptr<SpanNode>;

    virtual ~SpanNode() = default;

    vespalib::string toString() const;
    virtual void accept(SpanTreeVisitor &visitor) const = 0;
};

std::ostream & operator << (std::ostream & os, const SpanNode & node);

}  // namespace document

