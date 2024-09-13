A logarithmic-shaped closeness; like normal closeness it goes from 1 to 0, but looks like [closeness plot](https://docs.vespa.ai/en/reference/rank-features.html#closeness). The function is a logarithmic fall-off based on `log(distance + scale)` and is calculated as:

$$closeness(name).logscale = \\frac{log(maxDistance + scale) - log(distance(name) + scale))}{(log(maxDistance + scale) - log(scale))}$$

where scale is defined using [halfResponse and maxDistance](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#closeness):

$$scale = \\frac{halfResponse\^2}{(maxDistance - 2 × halfResponse)}$$

When `distance(name) == halfResponse` the function output is 0.5; halfResponse should be less than `maxDistance/2` since that means adding a certain distance when you are close matters more than adding the same distance when you're already far away.

Default: 0