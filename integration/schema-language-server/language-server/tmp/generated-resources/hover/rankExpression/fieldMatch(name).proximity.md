Normalized proximity - a value which is close to 1 when matched terms are close *inside each segment*, and close to zero when they are far apart inside segments. Relatively more connected terms influence this value more. This is absoluteProximity/average connectedness for the query terms for this field.

Note that if all the terms are far apart, the proximity will be 1, but the number of segments will be high. Proximity is only concerned with closeness within segments, a total score must also take the number of segments into account.

Default: 0