# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# Author: ingridhabrekke

import math
import torch
import torch.onnx
import torch.nn as nn


# A tiny encoder with one self-attention layer that, like BERT, does not attend to tokens with attention mask 0.
# Its output for the real tokens is therefore unchanged by padding, and by batching with other sequences,
# which is what the padding and batching tests verify. The other dummy models do not mask attention.
# The attention is written out rather than using nn.MultiheadAttention, whose ONNX export fixes the sequence length.
class MaskedAttentionEmbedder(nn.Module):
    def __init__(self, vocab_size, emb_size, max_positions):
        super(MaskedAttentionEmbedder, self).__init__()
        self.tokens = nn.Embedding(vocab_size, emb_size)
        self.positions = nn.Embedding(max_positions, emb_size)
        self.query = nn.Linear(emb_size, emb_size)
        self.key = nn.Linear(emb_size, emb_size)
        self.value = nn.Linear(emb_size, emb_size)
        self.scale = 1 / math.sqrt(emb_size)

    def forward(self, input_ids, attention_mask):
        # [batch, tokens] -> [batch, tokens, emb]
        positions = torch.arange(input_ids.shape[1]).unsqueeze(0)
        hidden = self.tokens(input_ids) + self.positions(positions)

        # Attention scores between all token pairs: [batch, tokens, tokens]
        scores = self.query(hidden) @ self.key(hidden).transpose(1, 2) * self.scale
        # Tokens with attention mask 0 get no attention from any token
        scores = scores.masked_fill(attention_mask.unsqueeze(1) == 0, -1e9)
        weights = torch.softmax(scores, dim=-1)
        return hidden + weights @ self.value(hidden)


def main():
    torch.manual_seed(42)
    vocabulary_size = 30522
    embedding_size = 16
    max_positions = 512
    model = MaskedAttentionEmbedder(vocabulary_size, embedding_size, max_positions)
    model.eval()

    # Omit training - just export randomly initialized network

    input_ids = torch.LongTensor([[101, 2023, 2003, 102]])
    attention_mask = torch.LongTensor([[1, 1, 1, 1]])
    torch.onnx.export(model,
                      (input_ids, attention_mask),
                      "dummy_masked_attention_embedder.onnx",
                      input_names = ["input_ids", "attention_mask"],
                      output_names = ["last_hidden_state"],
                      dynamic_axes = {
                          "input_ids": {0:"batch", 1:"tokens"},
                          "attention_mask": {0:"batch", 1:"tokens"},
                          "last_hidden_state": {0:"batch", 1:"tokens"},
                      },
                      opset_version=14)


if __name__ == "__main__":
    main()
