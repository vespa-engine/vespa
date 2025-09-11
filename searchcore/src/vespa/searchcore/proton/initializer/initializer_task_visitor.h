// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace proton {
    class AttributeInitializer;
}

namespace proton::initializer {

/**
 * Visitor for visiting an InitializerTask and its dependencies.
 *
 * InitializerTask has an acceptVisitor(InitializerTaskVisitor &visitor) method that
 * calls itself on all dependencies. A subclass of InitializerTask can override
 * this method and then call, e.g., visitAttributeInitializer.
 *
 * Additional virtual methods have to be added to InitializerTaskVisitor if more classes
 * should be visitable.
 */
class InitializerTaskVisitor {
public:
    virtual ~InitializerTaskVisitor() = default;
    virtual void visitAttributeInitializer(AttributeInitializer& /*attributeInitializer*/) {}
};

} // namespace proton::initializer
