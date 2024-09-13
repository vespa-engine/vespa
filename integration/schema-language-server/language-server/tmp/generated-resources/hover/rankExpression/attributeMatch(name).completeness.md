The normalized total completeness, where field completeness is more important:

`queryCompleteness * ( 1 - `[fieldCompletenessImportance](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#attributeMatch)` + `[fieldCompletenessImportance](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#attributeMatch)` * fieldCompleteness )`

Default: 0