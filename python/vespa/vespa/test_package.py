import unittest

from vespa.package import Field, Document, FieldSet


class TestField(unittest.TestCase):
    def test_field_name_type(self):
        field = Field(name="test_name", type="string")
        self.assertEqual(field.name, "test_name")
        self.assertEqual(field.type, "string")
        self.assertEqual(field.to_dict, {"name": "test_name", "type": "string"})
        self.assertEqual(field, Field(name="test_name", type="string"))
        self.assertEqual(field, Field.from_dict(field.to_dict))

    def test_field_name_type_indexing_index(self):
        field = Field(
            name="body",
            type="string",
            indexing=["index", "summary"],
            index=["enable-bm25"],
        )
        self.assertEqual(field.name, "body")
        self.assertEqual(field.type, "string")
        self.assertEqual(field.indexing, ["index", "summary"])
        self.assertEqual(field.index, ["enable-bm25"])
        self.assertEqual(
            field.to_dict,
            {
                "name": "body",
                "type": "string",
                "indexing": ["index", "summary"],
                "index": ["enable-bm25"],
            },
        )
        self.assertEqual(
            field,
            Field(
                name="body",
                type="string",
                indexing=["index", "summary"],
                index=["enable-bm25"],
            ),
        )
        self.assertEqual(field, Field.from_dict(field.to_dict))


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
            index=["enable-bm25"],
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
