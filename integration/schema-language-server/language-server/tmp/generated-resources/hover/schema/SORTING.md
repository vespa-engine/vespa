## sorting

Contained in [attribute](https://docs.vespa.ai/en/reference/schema-reference.html#attribute) or [field](https://docs.vespa.ai/en/reference/schema-reference.html#field). Specifies how sorting should be done.

```
sorting : [property]
```

or

```
sorting {
    [property]
    …
}
```

| Property |                                                                                                                                                                                            Description                                                                                                                                                                                            |
|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| order    | `ascending` (default) or `descending`. Used unless overridden using [order by](https://docs.vespa.ai/en/reference/query-language-reference.html#function) in query.                                                                                                                                                                                                                               |
| function | [Sort function](https://docs.vespa.ai/en/reference/query-language-reference.html#function): `uca` (default), `lowercase` or `raw`. Note that if no language or locale is specified in the query, the field, or generally for the query, `lowercase` will be used instead of `uca`. See [order by](https://docs.vespa.ai/en/reference/query-language-reference.html#order-by) for details.         |
| strength | [UCA sort strength](https://docs.vespa.ai/en/reference/query-language-reference.html#strength), default `primary` - see [strength](https://docs.vespa.ai/en/reference/query-language-reference.html#strength) for values. Values set in the query overrides the schema definition.                                                                                                                |
| locale   | [UCA locale](https://docs.vespa.ai/en/reference/query-language-reference.html#locale), default none, indicating that it is inferred from query. It should only be set here if the attribute is filled with data in one language only. See [locale](https://docs.vespa.ai/en/reference/query-language-reference.html#locale) for details. Values set in the query overrides the schema definition. |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#sorting)
