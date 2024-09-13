## field

Contained in [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema), [document](https://docs.vespa.ai/en/reference/schema-reference.html#document), [struct](https://docs.vespa.ai/en/reference/schema-reference.html#struct) or [annotation](https://docs.vespa.ai/en/reference/schema-reference.html#annotation). Defines a named value with a type and (optionally) how this field should be stored, indexed, searched, presented and how it should influence ranking.

```
field [name] type [type-name] {
    [body]
}
```

Do not use names that are used for other purposes in the indexing language or other places in the schema file. Reserved names are:

* attribute
* body
* case
* context
* documentid
* else
* header
* hit
* host
* if
* index
* position
* reference
* relevancy
* sddocname
* summary
* switch
* tokenize

Other names not to use include any words that start with a number or includes special characters.

The *type* attribute is mandatory - supported types:

|             Field type              |                                                                                                                                         Description                                                                                                                                         |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| annotationreference                 | Use to define a field (inside [annotation](https://docs.vespa.ai/en/reference/schema-reference.html#annotation), or inside e.g. a struct used by a field in an [annotation](https://docs.vespa.ai/en/reference/schema-reference.html#annotation)) with a reference to another annotation.   |
| array\<type\>                       | For single-value (primitive) types, use array\<type\> to create an array field of the element type.                                                                                                                                                                                         |
| bool                                | Use for boolean values.                                                                                                                                                                                                                                                                     |
| byte                                | Use for single 8-bit numbers.                                                                                                                                                                                                                                                               |
| double                              | Use for high precision floating point numbers (64-bit IEEE 754 double).                                                                                                                                                                                                                     |
| float                               | Use for floating point numbers (32-bit IEEE 754 float).                                                                                                                                                                                                                                     |
| int                                 | Use for single 32-bit integers.                                                                                                                                                                                                                                                             |
| long                                | Use for single 64-bit integers.                                                                                                                                                                                                                                                             |
| map\<key-type,value-type\>          | Use to create a map where each unique key is mapped to a single value.                                                                                                                                                                                                                      |
| position                            | Used to filter and/or rank documents by distance to a position in the query, see [Geo search](https://docs.vespa.ai/en/geo-search.html).                                                                                                                                                    |
| predicate                           | Use to match queries to a set of boolean constraints.                                                                                                                                                                                                                                       |
| raw                                 | Use for binary data .                                                                                                                                                                                                                                                                       |
| reference\<document-type\>          | A *reference\<document-type\>* field is a reference to an instance of a document-type - i.e. a foreign key.                                                                                                                                                                                 |
| string                              | Use for a text field of any length.                                                                                                                                                                                                                                                         |
| struct                              | Use to define a field with a struct datatype.                                                                                                                                                                                                                                               |
| tensor(dimension-1,...,dimension-N) | Use to create a tensor field with the given [tensor type spec](https://docs.vespa.ai/en/reference/tensor.html#tensor-type-spec) that can be used for [ranking](https://docs.vespa.ai/en/ranking.html) and [nearest neighbor search](https://docs.vespa.ai/en/nearest-neighbor-search.html). |
| uri                                 | Use for URL type matching.                                                                                                                                                                                                                                                                  |
| weightedset\<element-type\>         | Use to create a multivalue field of the element type, where each element is assigned a signed 32-bit integer weight.                                                                                                                                                                        |

The body of a field is optional for [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema), [document](https://docs.vespa.ai/en/reference/schema-reference.html#document) and [struct](https://docs.vespa.ai/en/reference/schema-reference.html#struct), and **disallowed** for [annotation](https://docs.vespa.ai/en/reference/schema-reference.html#annotation). It may contain the following elements:

|                                              Name                                              |  Occurrence   |                                                                                                                            Description                                                                                                                             |
|------------------------------------------------------------------------------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| alias                                                                                          | Zero to many. | Make an index or attribute available in queries under an additional name. This has minimal performance impact and can safely be added to running applications. Example: ``` field artist type string { alias: artist_name } ```                                    |
| [attribute](https://docs.vespa.ai/en/reference/schema-reference.html#attribute)                | Zero to many. | Specify an attribute setting.                                                                                                                                                                                                                                      |
| [bolding](https://docs.vespa.ai/en/reference/schema-reference.html#bolding)                    | Zero to one.  | Specifies whether the content of this field should be bolded. Only supported for [index](https://docs.vespa.ai/en/reference/schema-reference.html#indexing-index) fields of type string or array\<string\>.                                                        |
| [id](https://docs.vespa.ai/en/reference/schema-reference.html#id)                              | Zero to one.  | Explicitly decide the numerical id of this field. Is normally not necessary, but can be used to save some disk space.                                                                                                                                              |
| [index](https://docs.vespa.ai/en/reference/schema-reference.html#index)                        | Zero to many. | Specify a parameter of an index.                                                                                                                                                                                                                                   |
| [indexing](https://docs.vespa.ai/en/reference/schema-reference.html#indexing)                  | Zero to one.  | The indexing statements used to create index structure additions from this field.                                                                                                                                                                                  |
| [match](https://docs.vespa.ai/en/reference/schema-reference.html#match)                        | Zero to one.  | Set the matching type to use for this field.                                                                                                                                                                                                                       |
| [normalizing](https://docs.vespa.ai/en/reference/schema-reference.html#normalizing)            | Zero or one.  | Specifies the kind of text normalizing to do on a string field.                                                                                                                                                                                                    |
| [query-command](https://docs.vespa.ai/en/reference/schema-reference.html#query-command)        | Zero to many. | Specifies a command which can be received by a plugin searcher in the Search Container.                                                                                                                                                                            |
| [rank](https://docs.vespa.ai/en/reference/schema-reference.html#rank)                          | Zero or one.  | Specify if the field is used for ranking.                                                                                                                                                                                                                          |
| [rank-type](https://docs.vespa.ai/en/reference/schema-reference.html#rank-type)                | Zero to one.  | Selects the set of low-level rank settings to be used for this field when using default `nativeRank`.                                                                                                                                                              |
| [sorting](https://docs.vespa.ai/en/reference/schema-reference.html#sorting)                    | Zero or one.  | The sort specification for this field.                                                                                                                                                                                                                             |
| [stemming](https://docs.vespa.ai/en/reference/schema-reference.html#stemming)                  | Zero or one.  | Specifies stemming options to use for this field.                                                                                                                                                                                                                  |
| [struct-field](https://docs.vespa.ai/en/reference/schema-reference.html#struct-field)          | Zero to many. | A subfield of a field of type struct. The struct must have been defined to contain this subfield in the struct definition. If you want the subfield to be handled differently from the rest of the struct, you may specify it within the body of the struct-field. |
| [summary](https://docs.vespa.ai/en/reference/schema-reference.html#summary)                    | Zero to many. | Sets a summary setting of this field, set to `dynamic` to make a dynamic summary.                                                                                                                                                                                  |
| [summary-to](https://docs.vespa.ai/en/reference/schema-reference.html#summary-to)              | Zero to one.  | <br /> **Deprecated:** Use [document-summary](https://docs.vespa.ai/en/reference/schema-reference.html#document-summary) instead. The list of document summary names this should be included in. <br />                                                            |
| [weight](https://docs.vespa.ai/en/reference/schema-reference.html#weight)                      | Zero to one.  | The importance of a field when searching multiple fields and using `nativeRank`.                                                                                                                                                                                   |
| [weightedset](https://docs.vespa.ai/en/reference/schema-reference.html#weightedset-properties) | Zero to one.  | Properties of a weightedset [weightedset\<element-type\>](https://docs.vespa.ai/en/reference/schema-reference.html#weightedset)                                                                                                                                    |

Fields can not have default values. See the [document guide](https://docs.vespa.ai/en/documents.html#fields) for how to auto-set field values.

It is not possible to query for fields without value (i.e. query for NULL) - see the [query language reference](https://docs.vespa.ai/en/reference/query-language-reference.html). Fields without value are not returned in [query results](https://docs.vespa.ai/en/reference/default-result-format.html).

Fields can be declared outside the document block in the schema. These fields are not part of the document type but behave like regular fields for queries. Since they are not part of the document they cannot be written directly, but instead take their values from document fields, using the `input` expression: `indexing: input my_document_field | embed | summary | index`

This is useful e.g. to index a field in multiple ways, or to change the field value, something which is not allowed with document fields. When the document field(s) used as input are updated, these fields are updated with them.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#field)
