A logarithmic-shaped freshness; also goes from 1 to 0, but looks like [freshness plot](https://docs.vespa.ai/en/reference/rank-features.html#freshness). The function is based on `-log(age(name) + scale)` and is calculated as:

$$\\frac{log(maxAge + scale) - log(age(name) + scale)}{log(maxAge + scale) - log(scale)}$$

where scale is defined using [halfResponse and maxAge](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#freshness):

$$\\frac{-halfResponse\^2}{2 × halfResponse - maxAge}$$

When `age(name) == halfResponse` the function output is 0.5.

Default: 0