## second-phase

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). The config specifying the second phase of ranking. See [phased ranking with Vespa](https://docs.vespa.ai/en/phased-ranking.html). This is the optional re-ranking phase performed on the top ranking hits from the `first-phase`, and where you should put any advanced relevancy calculations. For example Machine Learned Ranking (MLR) models. By default, no second-phase ranking is performed.

```
second-phase {
    [body]
}
```

The body of a secondphase-ranking statement consists of:

|                                       Name                                        |                                                                                                                                                                                                                           Description                                                                                                                                                                                                                           |
|-----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [expression](https://docs.vespa.ai/en/reference/schema-reference.html#expression) | Specify the ranking expression to be used for second phase of ranking. (for a description, see the [ranking expression](https://docs.vespa.ai/en/reference/ranking-expressions.html) documentation. Hits not reranked might be rescored using a linear function to avoid a greater rank score than the worst reranked hit. This linear function will normally attempt to map the first phase rank score range of reranked hits to the reranked rank score range |
| rank-score-drop-limit                                                             | When set, drop all hits with a second phase rank score (possibly a [rescored](https://docs.vespa.ai/en/reference/schema-reference.html#secondphase-rescoring) rank score) less than or equal to this floating point number. Use this to implement a second phase rank cutoff. By default, this value is not set. This can also be [set in the query](https://docs.vespa.ai/en/reference/query-api-reference.html#ranking.secondphase.rankscoredroplimit).       |
| rerank-count                                                                      | Optional argument. Specifies the number of hits to be re-ranked in the second phase. Default value is 100. This can also be [set in the query](https://docs.vespa.ai/en/reference/query-api-reference.html#ranking.rerankcount). Note that this value is local to each node involved in a query. Hits not reranked might be [rescored](https://docs.vespa.ai/en/reference/schema-reference.html#secondphase-rescoring).                                         |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#secondphase-rank)
