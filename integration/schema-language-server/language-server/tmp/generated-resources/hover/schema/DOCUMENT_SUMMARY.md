## document-summary

Contained in [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema). An explicitly defined document summary. By default, a document summary named `default` is created. Using this element, other document summaries containing a different set of fields can be created.

```
document-summary [name] inherits [document-summary1], [document-summary2], ... {
    [body]
}
```

The `inherits` attribute is optional. If defined, it contains the name of other document summaries in the same schema (or a parent) which this should inherit the fields of. Refer to [schema inheritance](https://docs.vespa.ai/en/schemas.html#schema-inheritance) for examples.

The body of a document summary consists of:

|                                    Name                                     |  Occurrence  |                                                                                                                                                                                                                                                   Description                                                                                                                                                                                                                                                   |
|-----------------------------------------------------------------------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| from-disk                                                                   | Zero or one  | Marks this summary as accessing fields on disk                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| [summary](https://docs.vespa.ai/en/reference/schema-reference.html#summary) | Zero to many | A summary field in this document summary.                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| omit-summary-features                                                       | Zero or one  | Specifies that [summary-features](https://docs.vespa.ai/en/reference/schema-reference.html#summary-features) should be omitted from this document summary. Use this to reduce CPU cost in [multiphase searching](https://docs.vespa.ai/en/searcher-development.html#multiphase-searching) when using multiple document summaries to fill hits, and only some of them need the summary features that are specified in the [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). |

Use the [summary](https://docs.vespa.ai/en/reference/query-api-reference.html#presentation.summary) query parameter to choose a document summary in searches or in [grouping](https://docs.vespa.ai/en/reference/grouping-syntax.html#summary). See also [document summaries](https://docs.vespa.ai/en/document-summaries.html).
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#document-summary)
