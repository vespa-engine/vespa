import torch
import torch.onnx
import torch.nn as nn
from torch.nn import TransformerEncoder, TransformerEncoderLayer


class TransformerModel(nn.Module):
    def __init__(self, vocab_size, emb_size, num_heads, hidden_dim_size, num_layers, dropout=0.2):
        super(TransformerModel, self).__init__()
        self.encoder = nn.Embedding(vocab_size, emb_size)
        encoder_layers = TransformerEncoderLayer(emb_size, num_heads, hidden_dim_size, dropout)
        self.transformer_encoder = TransformerEncoder(encoder_layers, num_layers)

    def forward(self, tokens, attention_mask):
        src = self.encoder((tokens * attention_mask))
        output = self.transformer_encoder(src)
        return output


def main():
    vocabulary_size = 20
    embedding_size = 16
    hidden_dim_size = 32
    num_layers = 2
    num_heads = 2
    model = TransformerModel(vocabulary_size, embedding_size, num_heads, hidden_dim_size, num_layers)

    # Omit training - just export randomly initialized network

    tokens = torch.LongTensor([[1,2,3,4,5]])
    attention_mask = torch.LongTensor([[1,1,1,1,1]])
    token_type_ids = torch.LongTensor([[0,0,0,0,0]])
    torch.onnx.export(model,
                      (tokens, attention_mask),
                      "dummy_transformer_without_type_ids.onnx",
                      input_names = ["input_ids", "attention_mask"],
                      output_names = ["output_0"],
                      dynamic_axes = {
                          "input_ids": {0:"batch", 1:"tokens"},
                          "attention_mask": {0:"batch", 1:"tokens"},
                          "output_0": {0:"batch", 1:"tokens"},
                      },
                      opset_version=12)


if __name__ == "__main__":
    main()


