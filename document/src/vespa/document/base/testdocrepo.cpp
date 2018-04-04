// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testdocrepo.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/config/print/fileconfigreader.h>

using document::config_builder::Struct;
using document::config_builder::Wset;
using document::config_builder::Array;
using document::config_builder::Map;

namespace document {

TestDocRepo::TestDocRepo()
    : _cfg(getDefaultConfig()),
      _repo(new DocumentTypeRepo(_cfg)) {
}

    TestDocRepo::~TestDocRepo() {}

DocumenttypesConfig TestDocRepo::getDefaultConfig() {
    const int type1_id = 238423572;
    const int type2_id = 238424533;
    const int type3_id = 1088783091;
    const int mystruct_id = -2092985851;
    const int structarray_id = 759956026;
    config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(type1_id, "testdoctype1",
                     Struct("testdoctype1.header")
                     .addField("headerval", DataType::T_INT)
                     .addField("headerlongval", DataType::T_LONG)
                     .addField("hfloatval", DataType::T_FLOAT)
                     .addField("hstringval", DataType::T_STRING)
                     .addField("mystruct", Struct("mystruct")
                               .setId(mystruct_id)
                               .addField("key", DataType::T_INT)
                               .addField("value", DataType::T_STRING))
                     .addField("tags", Array(DataType::T_STRING))
                     .addField("stringweightedset", Wset(DataType::T_STRING))
                     .addField("stringweightedset2", DataType::T_TAG)
                     .addField("byteweightedset", Wset(DataType::T_BYTE))
                     .addField("mymap",
                               Map(DataType::T_INT, DataType::T_STRING))
                     .addField("structarrmap", Map(
                                     DataType::T_STRING,
                                     Array(mystruct_id).setId(structarray_id)))
                     .addField("title", DataType::T_STRING)
                     .addField("byteval", DataType::T_BYTE),
                     Struct("testdoctype1.body")
                     .addField("content", DataType::T_STRING)
                     .addField("rawarray", Array(DataType::T_RAW))
                     .addField("structarray", structarray_id)
                     .addField("tensor", DataType::T_TENSOR));
    builder.document(type2_id, "testdoctype2",
                     Struct("testdoctype2.header")
                     .addField("onlyinchild", DataType::T_INT),
                     Struct("testdoctype2.body"))
        .inherit(type1_id);
    builder.document(type3_id, "_test_doctype3_",
                     Struct("_test_doctype3_.header")
                     .addField("_only_in_child_", DataType::T_INT),
                     Struct("_test_doctype3_.body"))
        .inherit(type1_id);
    return builder.config();
}

const DataType*
TestDocRepo::getDocumentType(const vespalib::string &t) const {
    return _repo->getDocumentType(t);
}

DocumenttypesConfig readDocumenttypesConfig(const char *file_name) {
    config::FileConfigReader<DocumenttypesConfig> reader(file_name);
    return DocumenttypesConfig(*reader.read());
}

DocumenttypesConfig readDocumenttypesConfig(const std::string &file_name ) {
    return readDocumenttypesConfig(file_name.c_str());
}

}  // namespace document
