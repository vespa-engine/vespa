Convenience function for generating token sequence input to Transformer models. Similar to the `tokenInputIds`, creates a tensor of type `d0[1],d1[length]` which represents a mask with zeros for the first input including CLS and SEP token, ones for the rest of the inputs (up to and including the final SEP token), and 0's for the rest of the tensor.

Default: n/a