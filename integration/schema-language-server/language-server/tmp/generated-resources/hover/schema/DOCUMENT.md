## document

Contained in [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema) and describes a document type. This can also be the root of the schema, if the document is not to be queried directly.

```
document [name] inherits [name-list] {
    [body]
}
```

The document name is optional, it defaults to the containing `schema` element's name. If there is no containing `schema` element, the document name is required. If the document with a name is defined inside a schema, the document name must match the `schema` element's name. The reference to *document type* in the documentation refers to the document name defined here.

The `inherits` attribute is optional and has as value a comma-separated list of names of other document types. A document type may inherit the fields of one or more other document types, see [document inheritance](https://docs.vespa.ai/en/schemas.html#schema-inheritance) for examples. If no document types are explicitly inherited, the document inherits the generic `document` type.

The body of a document type is optional and may contain:

|                                        Name                                         |  Occurrence  |                                  Description                                  |
|-------------------------------------------------------------------------------------|--------------|-------------------------------------------------------------------------------|
| [struct](https://docs.vespa.ai/en/reference/schema-reference.html#struct)           | Zero to many | A struct type definition for this document.                                   |
| [field](https://docs.vespa.ai/en/reference/schema-reference.html#field)             | Zero to many | A field of this document.                                                     |
| [compression](https://docs.vespa.ai/en/reference/schema-reference.html#compression) | Zero to one  | Specifies compression options for documents of this document type in storage. |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#document)
