## summary-features

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). List of [rank features](https://docs.vespa.ai/en/reference/rank-features.html) to be included with each result hit, in the [summaryfeatures](https://docs.vespa.ai/en/reference/default-result-format.html#summaryfeatures) field. Also see [feature values in results](https://docs.vespa.ai/en/ranking-expressions-features.html#accessing-feature-function-values-in-results).

If not specified, the features are as specified in the parent profile (if any). To inherit the features from the parent profile *and* specify additional features, specify explicitly that the features should be inherited from the parent as shown below. Refer to [schema inheritance](https://docs.vespa.ai/en/schemas.html#schema-inheritance) for examples.

The rank features specified here are computed in the [fill phase](https://docs.vespa.ai/en/searcher-development.html#multiphase-searching) of multiphased queries.  
**Note:** Rank-features references in *summary-features* are **re-calculated** during the *fill protocol phase* for the hits which made it into the global top ranking hits (from all nodes). See [match-features](https://docs.vespa.ai/en/reference/schema-reference.html#match-features) for an alternative.

```
summary-features: [feature] [feature]…
```

or

```
summary-features [inherits parent-profile]? {
    [feature]
    [feature]
}
```

Any number of rank features separated by space can be listed on each line.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#summary-features)
