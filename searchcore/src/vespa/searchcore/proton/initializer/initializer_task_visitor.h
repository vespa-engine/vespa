// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace proton {
    class AttributeInitializer;
}

namespace proton::initializer {

class InitializerTaskVisitor {
public:
    virtual ~InitializerTaskVisitor() = default;
    virtual void visitAttributeInitializer(AttributeInitializer& /*attributeInitializer*/) {}
};

} // namespace proton::initializer
