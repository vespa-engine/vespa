# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import torch
import torch.onnx
import torch.nn as nn
from torch.nn import TransformerEncoder, TransformerEncoderLayer 


class MaskedTransformerModel(nn.Module):
    def __init__(self, vocab_size, emb_size, num_heads, hidden_dim_size, num_layers, dropout=0.2):
        super(MaskedTransformerModel, self).__init__()
        self.encoder = nn.Embedding(vocab_size, emb_size)
        encoder_layers = TransformerEncoderLayer(emb_size, num_heads, hidden_dim_size, dropout)
        self.transformer_encoder = TransformerEncoder(encoder_layers, num_layers)
        self.fc = nn.Linear(emb_size,vocab_size) 

    def forward(self, tokens, attention_mask, token_type_ids):
        src = self.encoder((tokens * attention_mask) + token_type_ids)
        output = self.transformer_encoder(src)
        output = self.fc(output)
        return output


def main():
    vocabulary_size = 30522 
    embedding_size = 16
    hidden_dim_size = 32
    num_layers = 2
    num_heads = 2
    model = MaskedTransformerModel(vocabulary_size, embedding_size, num_heads, hidden_dim_size, num_layers)

    # Omit training - just export randomly initialized network

    tokens = torch.LongTensor([[1,2,3,4,5]])
    attention_mask = torch.LongTensor([[1,1,1,1,1]])
    token_type_ids = torch.LongTensor([[0,0,0,0,0]])
    torch.onnx.export(model,
                      (tokens, attention_mask, token_type_ids),
                      "dummy_transformer_mlm.onnx",
                      input_names = ["input_ids", "attention_mask", "token_type_ids"],
                      output_names = ["logits"],
                      dynamic_axes = {
                          "input_ids": {0:"batch", 1:"tokens"},
                          "attention_mask": {0:"batch", 1:"tokens"},
                          "token_type_ids": {0:"batch", 1:"tokens"},
                          "logits": {0:"batch", 1:"tokens"},
                      },
                      opset_version=12)


if __name__ == "__main__":
    main()


