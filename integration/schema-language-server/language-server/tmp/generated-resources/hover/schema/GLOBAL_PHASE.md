## global-phase

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). The config specifying the global phase of ranking. See [phased ranking with Vespa](https://docs.vespa.ai/en/phased-ranking.html). This is an optional re-ranking phase performed on the top ranking hits in the stateless container after merging hits from all the content nodes. The "top ranking" here means as scored by the first-phase ranking expression or (if specified) second-phase ranking expression. Typically used for computing large ONNX models which would be expensive to compute on all content nodes. By default, no global-phase ranking is performed.

```
global-phase {
    [body]
}
```

The body of a global-phase ranking statement consists of:

|                                       Name                                        |                                                                                                                                            Description                                                                                                                                             |
|-----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [expression](https://docs.vespa.ai/en/reference/schema-reference.html#expression) | Specify the ranking expression to be used for global phase of ranking. (for a description, see the [ranking expression](https://docs.vespa.ai/en/reference/ranking-expressions.html) documentation.                                                                                                |
| rerank-count                                                                      | Optional argument. Specifies the number of hits to be re-ranked in the global phase. Default value is 100. Note for complex setups: Applied to hits from one schema at a time, so if a query searches in multiple schemas simultaneously, global-phase may run for 100 hits per schema as default. |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#globalphase-rank)
