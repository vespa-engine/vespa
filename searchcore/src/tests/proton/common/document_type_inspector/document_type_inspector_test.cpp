// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("document_type_inspector_test");

#include <vespa/searchcore/proton/common/document_type_inspector.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace document;
using namespace proton;
using namespace search::index;

Schema
getSchema()
{
    Schema schema;
    schema.addSummaryField(Schema::SummaryField("f1", Schema::STRING));
    schema.addSummaryField(Schema::SummaryField("f2", Schema::STRING));
    return schema;
}

struct Fixture
{
    Schema _schema;
    DocBuilder _builder;
    DocumentTypeInspector _inspector;
    Fixture()
        : _schema(getSchema()),
          _builder(_schema),
          _inspector(_builder.getDocumentType())
    {
    }
};

TEST_F("require that existing fields are known", Fixture)
{
    EXPECT_TRUE(f._inspector.hasField("f1"));
    EXPECT_TRUE(f._inspector.hasField("f2"));
}

TEST_F("require that non-existing fields are NOT known", Fixture)
{
    EXPECT_FALSE(f._inspector.hasField("not"));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
