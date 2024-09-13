## rank-features

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). List of extra [rank features](https://docs.vespa.ai/en/reference/rank-features.html) to be dumped when using the query-argument [rankfeatures](https://docs.vespa.ai/en/reference/query-api-reference.html#ranking.listfeatures).

```
rank-features: [feature] [feature]
```

or

```
rank-features {
    [feature]
    [feature]
}
```

Any number of ranking features can be listed on each line, separated by space.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#rank-features)
