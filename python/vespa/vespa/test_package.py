import unittest

from vespa.package import Field


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
        print(str(field))
