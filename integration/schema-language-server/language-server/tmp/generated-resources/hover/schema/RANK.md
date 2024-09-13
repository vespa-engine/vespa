## rank

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field), [struct-field](https://docs.vespa.ai/en/reference/schema-reference.html#struct-field) or [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). Set the kind of ranking calculations which will be done for the field. Even though the actual ranking expressions decide the ranking, this setting tells Vespa which preparatory calculations and which data structures are needed for the field.

```
rank [field-name]: [ranking settings]
```

or

```
rank {
    [ranking setting]
}
```

The field name should only be specified when used inside a rank-profile. The following ranking settings are supported in addition to the default:

| Ranking setting |                                                                                                                                                                                                                                                                                                                                                                                                                                                                 Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| filter          | Indicates that matching in this field should use fast bit vector data structures only. This saves CPU during matching, but only a few simple ranking features will be available for the field. This setting is appropriate for fields typically used for filtering or simple boosting purposes, like filtering or boosting on the language of the document. For *index* fields, this setting does not change index formats but helps choose the most compact representation when matching against the field. For *attribute* fields with *fast-search* this setting builds additional posting list representations (bit vectors) which can speed up query evaluation significantly. See [feature tuning](https://docs.vespa.ai/en/performance/feature-tuning.html#when-to-use-fast-search-for-attribute-fields) and [the practical search performance guide](https://docs.vespa.ai/en/performance/practical-search-performance-guide.html). |
| normal          | The reverse of `filter`. Matching in this field will use normal data structures and give normal match information for ranking. Used to turn off implicit `rank: filter` when using [match: exact](https://docs.vespa.ai/en/reference/schema-reference.html#exact). If both `filter` and `normal` are set somehow, the effect is as if only `normal` was specified.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |

Related: See the [filter](https://docs.vespa.ai/en/reference/query-language-reference.html#filter) query annotation for how to annotate query terms as filters.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#rank)
