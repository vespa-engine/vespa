A number which is close to 1 if the position in attribute *name* is close to the query position compared to [maxDistance](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#closeness):

`max(1-distance(name)/maxDistance , 0)`

Scales linearly with distance, see [closeness plot](https://docs.vespa.ai/en/reference/rank-features.html#closeness).

Default: 0