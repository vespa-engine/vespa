## first-phase

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). The config specifying the first phase of ranking. See [phased ranking with Vespa](https://docs.vespa.ai/en/phased-ranking.html). This is the initial ranking performed on all matching documents, you should therefore avoid doing computationally expensive relevancy calculations here. By default, this will use the ranking feature `nativeRank`.

```
first-phase {
    [body]
}
```

The body of a firstphase-ranking statement consists of:

|                                       Name                                        |                                                                            Description                                                                            |
|-----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [expression](https://docs.vespa.ai/en/reference/schema-reference.html#expression) | Specify the ranking expression to be used for first phase of ranking - see [ranking expressions](https://docs.vespa.ai/en/reference/ranking-expressions.html).    |
| keep-rank-count                                                                   | How many documents to keep the first phase top rank values for. Default value is 10000.                                                                           |
| rank-score-drop-limit                                                             | Drop all hits with a first phase rank score less than or equal to this floating point number. Use this to implement a rank cutoff. Default is `-Double.MAX_VALUE` |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#firstphase-rank)
