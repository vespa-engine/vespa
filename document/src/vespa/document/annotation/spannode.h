// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/util/printable.h>
#include <memory>

namespace document {
class SpanTreeVisitor;

struct SpanNode : Printable {
    typedef std::unique_ptr<SpanNode> UP;

    virtual ~SpanNode() {}

    virtual void accept(SpanTreeVisitor &visitor) const = 0;
};

}  // namespace document

