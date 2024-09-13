## match-features

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). List of [rank features](https://docs.vespa.ai/en/reference/rank-features.html) to be included with each result hit, in the [matchfeatures](https://docs.vespa.ai/en/reference/default-result-format.html#matchfeatures) field. Also see [feature values in results](https://docs.vespa.ai/en/ranking-expressions-features.html#accessing-feature-function-values-in-results).

If not specified, the features are as specified in the parent profile (if any). To inherit the features from the parent profile *and* specify additional features, specify explicitly that the features should be inherited from the parent as shown below, also see [schema inheritance](https://docs.vespa.ai/en/schemas.html#schema-inheritance).

To disable match-features from parent rank profiles, use `match-features {}`.

*match-features* is similar to [summary-features](https://docs.vespa.ai/en/reference/schema-reference.html#summary-features), but the rank features specified here are computed in the *first protocol phase* of [multi-protocol query execution](https://docs.vespa.ai/en/searcher-development.html#multiphase-searching), also called the *match* protocol phase. This gives a different performance trade-off, for details see [feature values in results](https://docs.vespa.ai/en/ranking-expressions-features.html#accessing-feature-function-values-in-results).

```
match-features: [feature] [feature]…
```

or

```
match-features [inherits parent-profile]? {
    [feature]
    [feature]
}
```

Any number of ranking features separated by space can be listed on each line.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#match-features)
