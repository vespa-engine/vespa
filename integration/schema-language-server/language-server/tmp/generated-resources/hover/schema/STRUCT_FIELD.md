## struct-field

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field) or [struct-field](https://docs.vespa.ai/en/reference/schema-reference.html#struct-field). Defines how this struct field (a subfield of a struct) should be stored, indexed, searched, presented and how it should influence ranking. The field in which this struct field is contained must be of type struct or a collection of type struct:

```
struct-field [name] {
    [body]
}
```

The body of a struct field is optional and may contain the following elements:

|                                      Name                                       |  Occurrence  |                                                                                                                                                                                  Description                                                                                                                                                                                   |
|---------------------------------------------------------------------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [indexing](https://docs.vespa.ai/en/reference/schema-reference.html#indexing)   | Zero to one  | The indexing statements used to create index structure additions from this field. For indexed search only `attribute` is supported, which makes the struct field a searchable in-memory attribute that can also be used for e.g. grouping and ranking. For [streaming search](https://docs.vespa.ai/en/streaming-search.html) `index` and `summary` are supported in addition. |
| [attribute](https://docs.vespa.ai/en/reference/schema-reference.html#attribute) | Zero to many | Specifies an attribute setting. For example `attribute:fast-search`.                                                                                                                                                                                                                                                                                                           |
| [rank](https://docs.vespa.ai/en/reference/schema-reference.html#rank)           | Zero to one  | Specifies [rank](https://docs.vespa.ai/en/reference/schema-reference.html#rank) settings                                                                                                                                                                                                                                                                                       |
| [match](https://docs.vespa.ai/en/reference/schema-reference.html#match)         | Zero to one  | Specifies [match](https://docs.vespa.ai/en/reference/schema-reference.html#match) settings                                                                                                                                                                                                                                                                                     |

If this struct field is of type struct (i.e. a nested struct), only [indexing:summary](https://docs.vespa.ai/en/reference/schema-reference.html#indexing) may be specified. See [array\<type\>](https://docs.vespa.ai/en/reference/schema-reference.html#array) for example use.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#struct-field)
