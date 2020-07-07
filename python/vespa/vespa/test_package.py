import unittest

from vespa.package import (
    Field,
    Document,
    FieldSet,
    RankProfile,
    Schema,
    ApplicationPackage,
)


class TestField(unittest.TestCase):
    def test_field_name_type(self):
        field = Field(name="test_name", type="string")
        self.assertEqual(field.name, "test_name")
        self.assertEqual(field.type, "string")
        self.assertEqual(field.to_dict, {"name": "test_name", "type": "string"})
        self.assertEqual(field, Field(name="test_name", type="string"))
        self.assertEqual(field, Field.from_dict(field.to_dict))
        self.assertIsNone(field.indexing_to_text)

    def test_field_name_type_indexing_index(self):
        field = Field(
            name="body",
            type="string",
            indexing=["index", "summary"],
            index="enable-bm25",
        )
        self.assertEqual(field.name, "body")
        self.assertEqual(field.type, "string")
        self.assertEqual(field.indexing, ["index", "summary"])
        self.assertEqual(field.index, "enable-bm25")
        self.assertEqual(
            field.to_dict,
            {
                "name": "body",
                "type": "string",
                "indexing": ["index", "summary"],
                "index": "enable-bm25",
            },
        )
        self.assertEqual(
            field,
            Field(
                name="body",
                type="string",
                indexing=["index", "summary"],
                index="enable-bm25",
            ),
        )
        self.assertEqual(field, Field.from_dict(field.to_dict))
        self.assertEqual(field.indexing_to_text, "index | summary")


class TestDocument(unittest.TestCase):
    def test_empty_document(self):
        document = Document()
        self.assertEqual(document.fields, [])
        self.assertEqual(document.to_dict, {"fields": []})
        self.assertEqual(document, Document.from_dict(document.to_dict))

    def test_document_one_field(self):
        document = Document()
        field = Field(name="test_name", type="string")
        document.add_fields(field)
        self.assertEqual(document.fields, [field])
        self.assertEqual(document, Document.from_dict(document.to_dict))
        self.assertEqual(document, Document([field]))

    def test_document_two_fields(self):
        document = Document()
        field_1 = Field(name="test_name", type="string")
        field_2 = Field(
            name="body",
            type="string",
            indexing=["index", "summary"],
            index="enable-bm25",
        )
        document.add_fields(field_1, field_2)
        self.assertEqual(document.fields, [field_1, field_2])
        self.assertEqual(document, Document.from_dict(document.to_dict))
        self.assertEqual(document, Document([field_1, field_2]))


class TestFieldSet(unittest.TestCase):
    def test_fieldset(self):
        field_set = FieldSet(name="default", fields=["title", "body"])
        self.assertEqual(field_set.name, "default")
        self.assertEqual(field_set.fields, ["title", "body"])
        self.assertEqual(field_set, FieldSet.from_dict(field_set.to_dict))
        self.assertEqual(field_set.fields_to_text, "title, body")


class TestRankProfile(unittest.TestCase):
    def test_rank_profile(self):
        rank_profile = RankProfile(name="bm25", first_phase="bm25(title) + bm25(body)")
        self.assertEqual(rank_profile.name, "bm25")
        self.assertEqual(rank_profile.first_phase, "bm25(title) + bm25(body)")
        self.assertEqual(rank_profile, RankProfile.from_dict(rank_profile.to_dict))

    def test_rank_profile_inherits(self):
        rank_profile = RankProfile(
            name="bm25", first_phase="bm25(title) + bm25(body)", inherits="default"
        )
        self.assertEqual(rank_profile.name, "bm25")
        self.assertEqual(rank_profile.first_phase, "bm25(title) + bm25(body)")
        self.assertEqual(rank_profile, RankProfile.from_dict(rank_profile.to_dict))


class TestSchema(unittest.TestCase):
    def test_schema(self):
        schema = Schema(
            name="test_schema",
            document=Document(fields=[Field(name="test_name", type="string")]),
            fieldsets=[FieldSet(name="default", fields=["title", "body"])],
            rank_profiles=[
                RankProfile(name="bm25", first_phase="bm25(title) + bm25(body)")
            ],
        )
        self.assertEqual(schema, Schema.from_dict(schema.to_dict))
        self.assertDictEqual(
            schema.rank_profiles,
            {"bm25": RankProfile(name="bm25", first_phase="bm25(title) + bm25(body)")},
        )
        schema.add_rank_profile(
            RankProfile(name="default", first_phase="NativeRank(title)")
        )
        self.assertDictEqual(
            schema.rank_profiles,
            {
                "bm25": RankProfile(
                    name="bm25", first_phase="bm25(title) + bm25(body)"
                ),
                "default": RankProfile(name="default", first_phase="NativeRank(title)"),
            },
        )


class TestApplicationPackage(unittest.TestCase):
    def setUp(self) -> None:
        test_schema = Schema(
            name="msmarco",
            document=Document(
                fields=[
                    Field(name="id", type="string", indexing=["attribute", "summary"]),
                    Field(
                        name="title",
                        type="string",
                        indexing=["index", "summary"],
                        index="enable-bm25",
                    ),
                    Field(
                        name="body",
                        type="string",
                        indexing=["index", "summary"],
                        index="enable-bm25",
                    ),
                ]
            ),
            fieldsets=[FieldSet(name="default", fields=["title", "body"])],
            rank_profiles=[
                RankProfile(name="default", first_phase="nativeRank(title, body)"),
                RankProfile(
                    name="bm25",
                    first_phase="bm25(title) + bm25(body)",
                    inherits="default",
                ),
            ],
        )
        self.app_package = ApplicationPackage(name="test_app", schema=test_schema)

    def test_application_package(self):
        self.assertEqual(
            self.app_package, ApplicationPackage.from_dict(self.app_package.to_dict)
        )

    def test_schema_to_text(self):
        expected_result = (
            "schema msmarco {\n"
            "    document msmarco {\n"
            "        field id type string {\n"
            "            indexing: attribute | summary\n"
            "        }\n"
            "        field title type string {\n"
            "            indexing: index | summary\n"
            "            index: enable-bm25\n"
            "        }\n"
            "        field body type string {\n"
            "            indexing: index | summary\n"
            "            index: enable-bm25\n"
            "        }\n"
            "    }\n"
            "    fieldset default {\n"
            "        fields: title, body\n"
            "    }\n"
            "    rank-profile default {\n"
            "        first-phase {\n"
            "            expression: nativeRank(title, body)\n"
            "        }\n"
            "    }\n"
            "    rank-profile bm25 inherits default {\n"
            "        first-phase {\n"
            "            expression: bm25(title) + bm25(body)\n"
            "        }\n"
            "    }\n"
            "}"
        )
        self.assertEqual(self.app_package.schema_to_text, expected_result)

    def test_hosts_to_text(self):
        expected_result = (
            '<?xml version="1.0" encoding="utf-8" ?>\n'
            "<!-- Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->\n"
            "<hosts>\n"
            '    <host name="localhost">\n'
            "        <alias>node1</alias>\n"
            "    </host>\n"
            "</hosts>"
        )
        self.assertEqual(self.app_package.hosts_to_text, expected_result)

    def test_services_to_text(self):
        expected_result = (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<services version="1.0">\n'
            '    <container id="test_app_container" version="1.0">\n'
            "        <search></search>\n"
            "        <document-api></document-api>\n"
            "    </container>\n"
            '    <content id="test_app_content" version="1.0">\n'
            '        <redundancy reply-after="1">1</redundancy>\n'
            "        <documents>\n"
            '            <document type="msmarco" mode="index"></document>\n'
            "        </documents>\n"
            "        <nodes>\n"
            '            <node distribution-key="0" hostalias="node1"></node>\n'
            "        </nodes>\n"
            "    </content>\n"
            "</services>"
        )

        self.assertEqual(self.app_package.services_to_text, expected_result)
