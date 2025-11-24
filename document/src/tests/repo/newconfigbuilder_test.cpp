// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for NewConfigBuilder

#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace document;
using namespace document::new_config_builder;

TEST(NewConfigBuilderTest, basic_document_creation) {
    NewConfigBuilder builder;
    builder.document("test");

    const auto& config = builder.config();

    // Should have base "document" type and "test" type
    ASSERT_EQ(2u, config.doctype.size());
    EXPECT_EQ("document", config.doctype[0].name);
    EXPECT_EQ("test", config.doctype[1].name);

    // Test type should inherit from document
    ASSERT_EQ(1u, config.doctype[1].inherits.size());
    EXPECT_EQ(config.doctype[0].idx, config.doctype[1].inherits[0].idx);
}

TEST(NewConfigBuilderTest, primitive_types) {
    NewConfigBuilder builder;

    (void)builder.primitiveType(DataType::T_INT);
    (void)builder.primitiveType(DataType::T_STRING);
    (void)builder.primitiveType(DataType::T_LONG);

    const auto& config = builder.config();

    // Base document should have all primitive types
    ASSERT_GE(config.doctype[0].primitivetype.size(), 12u);

    // Check that primitive types have been registered
    bool found_int = false, found_string = false, found_long = false;
    for (const auto& pt : config.doctype[0].primitivetype) {
        if (pt.name == "int") found_int = true;
        if (pt.name == "string") found_string = true;
        if (pt.name == "long") found_long = true;
    }

    EXPECT_TRUE(found_int);
    EXPECT_TRUE(found_string);
    EXPECT_TRUE(found_long);
}

TEST(NewConfigBuilderTest, document_with_primitive_fields) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    doc.addField("int_field", builder.primitiveType(DataType::T_INT));
    doc.addField("string_field", builder.primitiveType(DataType::T_STRING));
    doc.addField("long_field", builder.primitiveType(DataType::T_LONG));

    const auto& config = builder.config();

    // Find the document type
    const auto* doctype = &config.doctype[1];
    ASSERT_EQ("mytype", doctype->name);

    // Find the contentstruct
    ASSERT_FALSE(doctype->structtype.empty());
    const auto* contentstruct = &doctype->structtype[0];
    EXPECT_EQ(doctype->contentstruct, contentstruct->idx);

    // Check fields
    ASSERT_EQ(3u, contentstruct->field.size());
    EXPECT_EQ("int_field", contentstruct->field[0].name);
    EXPECT_EQ("string_field", contentstruct->field[1].name);
    EXPECT_EQ("long_field", contentstruct->field[2].name);
}

TEST(NewConfigBuilderTest, array_type) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    auto string_ref = builder.primitiveType(DataType::T_STRING);
    auto array_ref = doc.registerArray(std::move(doc.createArray(string_ref)));

    doc.addField("string_array", array_ref);

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    // Check array type exists
    ASSERT_EQ(1u, doctype->arraytype.size());
    EXPECT_EQ(string_ref.idx, doctype->arraytype[0].elementtype);
    EXPECT_EQ(array_ref.idx, doctype->arraytype[0].idx);

    // Check field uses array type
    const auto* contentstruct = &doctype->structtype[0];
    ASSERT_EQ(1u, contentstruct->field.size());
    EXPECT_EQ("string_array", contentstruct->field[0].name);
    EXPECT_EQ(array_ref.idx, contentstruct->field[0].type);
}

TEST(NewConfigBuilderTest, map_type) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    auto int_ref = builder.primitiveType(DataType::T_INT);
    auto string_ref = builder.primitiveType(DataType::T_STRING);
    auto map_ref = doc.registerMap(std::move(doc.createMap(int_ref, string_ref)));

    doc.addField("int_string_map", map_ref);

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    // Check map type exists
    ASSERT_EQ(1u, doctype->maptype.size());
    EXPECT_EQ(int_ref.idx, doctype->maptype[0].keytype);
    EXPECT_EQ(string_ref.idx, doctype->maptype[0].valuetype);
    EXPECT_EQ(map_ref.idx, doctype->maptype[0].idx);
}

TEST(NewConfigBuilderTest, wset_type) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    auto string_ref = builder.primitiveType(DataType::T_STRING);
    auto wset_ref = doc.registerWset(std::move(doc.createWset(string_ref).removeIfZero()));

    doc.addField("string_wset", wset_ref);

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    // Check wset type exists
    ASSERT_EQ(1u, doctype->wsettype.size());
    EXPECT_EQ(string_ref.idx, doctype->wsettype[0].elementtype);
    EXPECT_EQ(wset_ref.idx, doctype->wsettype[0].idx);
    EXPECT_TRUE(doctype->wsettype[0].removeifzero);
}

TEST(NewConfigBuilderTest, struct_type) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    auto int_ref = builder.primitiveType(DataType::T_INT);
    auto string_ref = builder.primitiveType(DataType::T_STRING);

    auto struct_ref = doc.registerStruct(std::move(
        doc.createStruct("mystruct")
           .addField("key", int_ref)
           .addField("value", string_ref)));

    doc.addField("struct_field", struct_ref);

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    // Check struct type exists (index 1 is the contentstruct, index 0 is from base)
    ASSERT_EQ(2u, doctype->structtype.size());
    const auto* mystruct = &doctype->structtype[1];
    EXPECT_EQ("mystruct", mystruct->name);
    EXPECT_EQ(struct_ref.idx, mystruct->idx);

    // Check struct fields
    ASSERT_EQ(2u, mystruct->field.size());
    EXPECT_EQ("key", mystruct->field[0].name);
    EXPECT_EQ(int_ref.idx, mystruct->field[0].type);
    EXPECT_EQ("value", mystruct->field[1].name);
    EXPECT_EQ(string_ref.idx, mystruct->field[1].type);
}

TEST(NewConfigBuilderTest, tensor_field) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    doc.addTensorField("sparse_tensor", "tensor(x{})");
    doc.addTensorField("dense_tensor", "tensor(x[10])");

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    // Check tensor types
    ASSERT_EQ(2u, doctype->tensortype.size());
    EXPECT_EQ("tensor(x{})", doctype->tensortype[0].detailedtype);
    EXPECT_EQ("tensor(x[10])", doctype->tensortype[1].detailedtype);

    // Check fields
    const auto* contentstruct = &doctype->structtype[0];
    ASSERT_EQ(2u, contentstruct->field.size());
    EXPECT_EQ("sparse_tensor", contentstruct->field[0].name);
    EXPECT_EQ("dense_tensor", contentstruct->field[1].name);
}

TEST(NewConfigBuilderTest, document_inheritance) {
    NewConfigBuilder builder;
    auto& parent = builder.document("parent");
    auto& child = builder.document("child");

    child.inherit(parent.idx());

    const auto& config = builder.config();
    const auto* child_doc = &config.doctype[2];

    ASSERT_EQ("child", child_doc->name);
    // Should inherit from both "document" and "parent"
    ASSERT_EQ(2u, child_doc->inherits.size());
    EXPECT_EQ(config.doctype[0].idx, child_doc->inherits[0].idx);  // base document
    EXPECT_EQ(config.doctype[1].idx, child_doc->inherits[1].idx);  // parent
}

TEST(NewConfigBuilderTest, imported_field) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    doc.imported_field("my_imported_field");
    doc.imported_field("another_imported_field");

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    ASSERT_EQ(2u, doctype->importedfield.size());
    EXPECT_EQ("my_imported_field", doctype->importedfield[0].name);
    EXPECT_EQ("another_imported_field", doctype->importedfield[1].name);
}

TEST(NewConfigBuilderTest, annotation_type) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    doc.annotationType(123, "my_annotation");

    auto string_ref = builder.primitiveType(DataType::T_STRING);
    doc.annotationType(456, "annotated_string", string_ref);

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    ASSERT_EQ(2u, doctype->annotationtype.size());
    EXPECT_EQ("my_annotation", doctype->annotationtype[0].name);
    EXPECT_EQ(123, doctype->annotationtype[0].internalid);

    EXPECT_EQ("annotated_string", doctype->annotationtype[1].name);
    EXPECT_EQ(456, doctype->annotationtype[1].internalid);
    EXPECT_EQ(string_ref.idx, doctype->annotationtype[1].datatype);
}

TEST(NewConfigBuilderTest, document_reference) {
    NewConfigBuilder builder;
    auto& target = builder.document("target");
    auto& doc = builder.document("mytype");

    auto ref_type = doc.referenceType(target.idx());
    doc.addField("target_ref", ref_type);

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[2];

    ASSERT_EQ(1u, doctype->documentref.size());
    EXPECT_EQ(target.idx(), doctype->documentref[0].targettype);
    EXPECT_EQ(ref_type.idx, doctype->documentref[0].idx);
}

TEST(NewConfigBuilderTest, field_set) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    doc.addField("field1", builder.primitiveType(DataType::T_INT));
    doc.addField("field2", builder.primitiveType(DataType::T_STRING));
    doc.addField("field3", builder.primitiveType(DataType::T_LONG));

    doc.fieldSet("myset", {"field1", "field2"});

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    ASSERT_EQ(1u, doctype->fieldsets.size());
    auto it = doctype->fieldsets.find("myset");
    ASSERT_NE(doctype->fieldsets.end(), it);
    ASSERT_EQ(2u, it->second.fields.size());
    EXPECT_EQ("field1", it->second.fields[0]);
    EXPECT_EQ("field2", it->second.fields[1]);
}

TEST(NewConfigBuilderTest, complex_nested_types) {
    NewConfigBuilder builder;
    auto& doc = builder.document("complex");

    auto int_ref = builder.primitiveType(DataType::T_INT);
    auto string_ref = builder.primitiveType(DataType::T_STRING);

    // Create a struct with nested types
    auto inner_struct_ref = doc.registerStruct(std::move(
        doc.createStruct("inner")
           .addField("id", int_ref)
           .addField("name", string_ref)));

    auto struct_array_ref = doc.registerArray(std::move(doc.createArray(inner_struct_ref)));

    auto outer_struct_ref = doc.registerStruct(std::move(
        doc.createStruct("outer")
           .addField("items", struct_array_ref)));

    doc.addField("complex_field", outer_struct_ref);

    const auto& config = builder.config();
    const auto* doctype = &config.doctype[1];

    // Should have 3 structs: contentstruct, inner, outer
    ASSERT_EQ(3u, doctype->structtype.size());

    // Should have 1 array: array of inner struct
    ASSERT_EQ(1u, doctype->arraytype.size());

    // Verify the structure
    const auto* inner = &doctype->structtype[1];
    EXPECT_EQ("inner", inner->name);
    EXPECT_EQ(2u, inner->field.size());

    const auto* outer = &doctype->structtype[2];
    EXPECT_EQ("outer", outer->name);
    ASSERT_EQ(1u, outer->field.size());
    EXPECT_EQ("items", outer->field[0].name);
    EXPECT_EQ(struct_array_ref.idx, outer->field[0].type);
}

TEST(NewConfigBuilderTest, can_create_document_type_repo) {
    NewConfigBuilder builder;
    auto& doc = builder.document("mytype");

    doc.addField("int_field", builder.primitiveType(DataType::T_INT));
    doc.addField("string_field", builder.primitiveType(DataType::T_STRING));

    const auto& config = builder.config();

    // Try to create a DocumentTypeRepo from the config
    DocumentTypeRepo repo(config);

    // Verify we can get the document type
    const DocumentType* dt = repo.getDocumentType("mytype");
    ASSERT_NE(nullptr, dt);
    EXPECT_EQ("mytype", dt->getName());

    // Verify fields exist
    ASSERT_TRUE(dt->hasField("int_field"));
    ASSERT_TRUE(dt->hasField("string_field"));
}

GTEST_MAIN_RUN_ALL_TESTS()
