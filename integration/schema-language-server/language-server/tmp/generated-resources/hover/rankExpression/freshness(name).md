A number which is close to 1 if the timestamp in attribute *name* is recent compared to the current time compared to [maxAge](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#freshness):

`max( 1-age(name)/maxAge , 0 )`

Scales linearly with age, see [freshness plot](https://docs.vespa.ai/en/reference/rank-features.html#freshness).

Default: 0