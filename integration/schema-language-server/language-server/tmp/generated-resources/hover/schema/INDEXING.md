## indexing

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field) or [struct-field](https://docs.vespa.ai/en/reference/schema-reference.html#struct-field). One or more Indexing Language instructions used to produce index, attribute and summary data from this field. Indexing instructions has pipeline semantics similar to unix shell commands. The value of the field enters the pipeline during indexing and the pipeline puts the value into the desired index structures, possibly doing transformations and pulling in other values along the way.

```
indexing: [index-statement]
```

or

```
indexing {
    [indexing-statement];
    [indexing-statement];
    …
}
```

If the field containing this is defined outside the document, it must start by an indexing statement which outputs a value (either "input \[fieldname\]" to fetch a field value, or a literal, e.g "some-value" ). Fields in documents will use the value of the enclosing field as input (input \[fieldname\]) if one isn't explicitly provided.

Specify the operations separated by the pipe (`|`) character. For advanced processing needs, use the [indexing language](https://docs.vespa.ai/en/reference/indexing-language-reference.html), or write a [document processor](https://docs.vespa.ai/en/document-processing.html). Supported expressions for fields are:

|  expression  |                                                                                                                                                                                                                                description                                                                                                                                                                                                                                 |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| attribute    | [Attribute](https://docs.vespa.ai/en/attributes.html) is used to make a field available for sorting, grouping, ranking and searching using [match](https://docs.vespa.ai/en/reference/schema-reference.html#match) mode `word`.                                                                                                                                                                                                                                            |
| index        | Creates a searchable [index](https://docs.vespa.ai/en/proton.html#index) for the values of this field using [match](https://docs.vespa.ai/en/reference/schema-reference.html#match) mode `text`. All strings are lower-cased before stored in the index. By default, the index name will be the same as the name of the schema field. Use a [fieldset](https://docs.vespa.ai/en/reference/schema-reference.html#fieldset) to combine fields in the same set for searching. |
| set_language | Sets document language - [details](https://docs.vespa.ai/en/reference/indexing-language-reference.html#set_language).                                                                                                                                                                                                                                                                                                                                                      |
| summary      | Includes the value of this field in a [summary](https://docs.vespa.ai/en/reference/indexing-language-reference.html#summary) field. Modify summary output by using [summary:](https://docs.vespa.ai/en/reference/schema-reference.html#summary) (e.g. to generate dynamic teasers).                                                                                                                                                                                        |

When combining both `index` and `attribute` in the indexing statement for a field, e.g `indexing: summary | attribute | index`, the [match](https://docs.vespa.ai/en/reference/schema-reference.html#match) mode becomes `text` for the field. So searches in this field will not search the contents in the [attribute](https://docs.vespa.ai/en/reference/schema-reference.html#attribute) but the index.

Find examples and more details in the [Text Matching](https://docs.vespa.ai/en/text-matching.html) guide.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#indexing)
