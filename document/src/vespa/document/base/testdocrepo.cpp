// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testdocrepo.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/config/print/fileconfigreader.hpp>

using document::new_config_builder::NewConfigBuilder;

namespace document {

TestDocRepo::TestDocRepo()
    : _cfg(getDefaultConfig()),
      _repo(new DocumentTypeRepo(_cfg)) {
}

TestDocRepo::~TestDocRepo() = default;

DocumenttypesConfig TestDocRepo::getDefaultConfig() {
    const int type1_id = 238423572;
    const int type2_id = 238424533;
    const int type3_id = 1088783091;
    const int mystruct_id = -2092985851;
    NewConfigBuilder builder;

    auto& doc1 = builder.document("testdoctype1", type1_id);

    // Create mystruct
    auto mystruct_ref = doc1.createStruct("mystruct")
            .setId(mystruct_id)
            .addField("key", builder.primitiveType(DataType::T_INT))
            .addField("value", builder.primitiveStringType()).ref();

    // Create structarray (array of mystruct)
    auto structarray_ref = doc1.createArray(mystruct_ref).ref();

    // Add all fields from header
    doc1.addField("headerval", builder.primitiveType(DataType::T_INT))
        .addField("headerlongval", builder.primitiveType(DataType::T_LONG))
        .addField("hfloatval", builder.primitiveType(DataType::T_FLOAT))
        .addField("hstringval", builder.primitiveStringType())
        .addField("mystruct", mystruct_ref)
        .addField("tags", doc1.createArray(builder.primitiveStringType()).ref())
        .addField("boolfield", builder.primitiveType(DataType::T_BOOL))
        .addField("stringweightedset", doc1.createWset(builder.primitiveStringType()).ref())
        .addField("stringweightedset2", builder.primitiveType(DataType::T_TAG))
        .addField("byteweightedset", doc1.createWset(builder.primitiveType(DataType::T_BYTE)).ref())
        .addField("mymap", doc1.createMap(builder.primitiveType(DataType::T_INT),
                                          builder.primitiveStringType()).ref())
        .addField("structarrmap", doc1.createMap(builder.primitiveStringType(),
                                                 structarray_ref).ref())
        .addField("title", builder.primitiveStringType())
        .addField("byteval", builder.primitiveType(DataType::T_BYTE));

    // Add all fields from body
    doc1.addField("content", builder.primitiveStringType())
        .addField("rawarray", doc1.createArray(builder.primitiveType(DataType::T_RAW)).ref())
        .addField("structarray", structarray_ref)
        .addTensorField("sparse_tensor", "tensor(x{})")
        .addTensorField("sparse_xy_tensor", "tensor(x{},y{})")
        .addTensorField("sparse_float_tensor", "tensor<float>(x{})")
        .addTensorField("dense_tensor", "tensor(x[2])");

    // Add imported field
    doc1.imported_field("my_imported_field");

    // Add fieldset
    doc1.fieldSet("[document]", {"headerval", "hstringval", "title"});

    // testdoctype2 inherits from testdoctype1
    auto& doc2 = builder.document("testdoctype2", type2_id);
    doc2.addField("onlyinchild", builder.primitiveType(DataType::T_INT))
        .inherit(doc1.idx());

    // _test_doctype3_ inherits from testdoctype1
    auto& doc3 = builder.document("_test_doctype3_", type3_id);
    doc3.addField("_only_in_child_", builder.primitiveType(DataType::T_INT))
        .inherit(doc1.idx());

    return builder.config();
}

const DataType*
TestDocRepo::getDocumentType(const std::string &t) const {
    return _repo->getDocumentType(t);
}

DocumenttypesConfig readDocumenttypesConfig(const char *file_name) {
    ::config::FileConfigReader<DocumenttypesConfig> reader(file_name);
    return DocumenttypesConfig(*reader.read());
}

DocumenttypesConfig readDocumenttypesConfig(const std::string &file_name ) {
    return readDocumenttypesConfig(file_name.c_str());
}

}  // namespace document
