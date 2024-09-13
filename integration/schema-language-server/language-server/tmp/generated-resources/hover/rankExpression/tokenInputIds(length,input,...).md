Convenience function for generating token sequence input to Transformer models. Creates a tensor with dimensions `d0[1], d1[length]`, where `d0` is the batch dimension and `d1` is the maximum length of the token sequence. Assumes the inputs are zero-padded tensors representing token sequences. The result is the token sequence:

`CLS + input_1 + SEP + input_2 + SEP + ... + 0's`

* *length*: The maximum length of the token sequence
* *input_N*: Where to retrieve input from. At least one is required.

The inputs are typically retrieved from the query, document attributes or constants. For instance, `tokenInputIds(128, query(my_input), attribute(my_field))` where input types are:

* `query(my_input): tensor(d0[32])`
* `attribute(my_field): tensor(d0[128])`

will create a tensor of type `d0[1],d1[128]` consisting of the CLS token `101`, the tokens from the query, the SEP token `102`, the tokens from the document field, the SEP token `102`, and 0's for the rest of the tensor.

Default: n/a