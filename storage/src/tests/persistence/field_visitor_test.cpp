// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/document/select/parser.h>
#include <vespa/storage/persistence/fieldvisitor.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>
#include <algorithm>
#include <memory>
#include <string>
#include <vector>

using namespace ::testing;
using document::Document;

namespace storage {

struct FieldVisitorTest : Test {
    document::TestDocMan _test_doc_mgr;

    [[nodiscard]] std::vector<std::string> fields_in_selection(const std::string& selection) const {
        document::BucketIdFactory id_factory;
        document::select::Parser parser(_test_doc_mgr.getTypeRepo(), id_factory);
        auto* doc_type = _test_doc_mgr.getTypeRepo().getDocumentType("testdoctype1");
        assert(doc_type);
        FieldVisitor visitor(*doc_type);

        auto sel_ast = parser.parse(selection);
        sel_ast->visit(visitor);
        auto field_set = visitor.steal_field_set();

        std::vector<std::string> field_names;
        for (const auto& f : field_set.getFields()) {
            field_names.emplace_back(f->getName());
        }
        std::sort(field_names.begin(), field_names.end());
        return field_names;
    }
};

TEST_F(FieldVisitorTest, fields_are_resolved_below_binary_operators) {
    EXPECT_THAT(fields_in_selection("testdoctype1.headerval == 0"), ElementsAre("headerval"));
    EXPECT_THAT(fields_in_selection("testdoctype1.headerval % 100 != 0"), ElementsAre("headerval"));
    EXPECT_THAT(fields_in_selection("testdoctype1.headerval % testdoctype1.headerlongval != testdoctype1.boolfield"),
                ElementsAre("boolfield", "headerlongval", "headerval"));
    EXPECT_THAT(fields_in_selection("testdoctype1.boolfield and (testdoctype1.headerval > 0)"),
                ElementsAre("boolfield", "headerval"));
    EXPECT_THAT(fields_in_selection("testdoctype1.boolfield or (testdoctype1.headerval > 0)"),
                ElementsAre("boolfield", "headerval"));
}

TEST_F(FieldVisitorTest, fields_are_resolved_below_unary_operators) {
    EXPECT_THAT(fields_in_selection("not testdoctype1.boolfield"), ElementsAre("boolfield"));
}

} // storage
