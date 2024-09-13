## rank-type

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field) or [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). Selects the low-level rank settings to be used for this field when using `nativeRank`.

```
rank-type [field-name]: [rank-type-name]
```

The field name can be skipped inside fields. Defined rank types are:

|   Type   |                                                                                                                                   Description                                                                                                                                    |
|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| identity | Used for fields which contains only what this document *is*, e.g. "Title". Complete identity hits will get a high rank.                                                                                                                                                          |
| about    | Some text which is (only) about this document, e.g. "Description". About hits get high rank on partial matches and higher for matches early in the text and repetitive matches. This is the default rank type.                                                                   |
| tags     | Used for simple tag fields of type tag. The tags rank type uses a logarithmic table to give more relative boost in the low range: As tags are added they should have significant impact on rank score, but as more and more tags are added, each new tag should contribute less. |
| empty    | Gives no relevancy effect on matches. Used for fields you just want to treat as filters.                                                                                                                                                                                         |

For `nativeRank` one can specify a rank type per field. If the supported rank types do not meet requirements, one can explicitly configure the native rank features using rank-properties. See the [native rank reference](https://docs.vespa.ai/en/reference/nativerank.html) for more information.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#rank-type)
