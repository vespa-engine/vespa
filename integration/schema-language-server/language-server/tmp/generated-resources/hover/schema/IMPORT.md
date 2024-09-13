## import field

Contained in [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema). Using a [reference](https://docs.vespa.ai/en/reference/schema-reference.html#reference) to a document type, import a field from that document type into this schema to be used for matching, ranking, grouping and sorting. Only attribute fields can be imported. Importing fields are not supported in [streaming search](https://docs.vespa.ai/en/streaming-search.html#differences-in-streaming-search).

The imported field inherits all but the following properties from the parent field:

* [attribute: fast-access](https://docs.vespa.ai/en/reference/schema-reference.html#attribute)

Refer to [parent/child](https://docs.vespa.ai/en/parent-child.html) for a complete example. Note that the imported field is put outside of the document type:

```
schema myschema {
    document myschema {
        field parentschema_ref type reference<parentschema> {
            indexing: attribute
        }
    }
    import field parentschema_ref.name as parent_name {}
}
```

Extra restrictions apply for some of the field types:

|    Field type     |                                                                                                                   Restriction                                                                                                                    |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| array of struct   | Can be imported if at least one of the struct fields has an attribute. All struct fields with attributes must have primitive types. Only the struct fields with attributes will be visible.                                                      |
| map of struct     | Can be imported if the key field has an attribute and at least one of the struct fields has an attribute. All struct fields with attributes must have primitive types. Only the key field and the struct fields with attributes will be visible. |
| map               | Can be imported if both key and value fields have primitive types and have attributes.                                                                                                                                                           |
| position          | Can be imported if it has an attribute.                                                                                                                                                                                                          |
| array of position | Can be imported if it has an attribute.                                                                                                                                                                                                          |

To use an imported field in summary, create an explicit [document summary](https://docs.vespa.ai/en/reference/schema-reference.html#document-summary) containing the field.

Imported fields can be used to expire documents, but [read this first](https://docs.vespa.ai/en/documents.html#document-expiry).
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#import-field)
