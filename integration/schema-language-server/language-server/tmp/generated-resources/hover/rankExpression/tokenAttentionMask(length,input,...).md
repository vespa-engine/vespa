Convenience function for generating token sequence input to Transformer models. Similar to the `tokenInputIds`, creates a tensor of type `d0[1],d1[length]` which represents a mask with ones for all tokens that are set (CLS and SEP and all inputs), and zeros for the rest.

Default: n/a