The normalized total completeness, where field completeness is more important:

`queryCompleteness * ( 1 - `[fieldCompletenessImportance](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#fieldMatch)` ) + `[fieldCompletenessImportance](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#fieldMatch)` * fieldCompleteness`

Default: 0