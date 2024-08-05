// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "objectvisitor.h"
#include <vector>
#include <memory>

namespace vespalib {

namespace slime { struct Cursor; }

/**
 * This is a concrete object visitor that will build up a structured
 * slime representation of an object.
 **/
class Object2Slime : public ObjectVisitor
{
private:
    std::reference_wrapper<slime::Cursor> _cursor;
    std::vector<std::reference_wrapper<slime::Cursor>> _stack;

public:
    explicit Object2Slime(slime::Cursor & cursor);
    ~Object2Slime() override;

    void openStruct(std::string_view name, std::string_view type) override;
    void closeStruct() override;
    void visitBool(std::string_view name, bool value) override;
    void visitInt(std::string_view name, int64_t value) override;
    void visitFloat(std::string_view name, double value) override;
    void visitString(std::string_view name, std::string_view value) override;
    void visitNull(std::string_view name) override;
    void visitNotImplemented() override;
};

} // namespace vespalib
