import torch
import torch.onnx
import torch.nn as nn
from torch.nn import TransformerEncoderLayer, TransformerEncoder, TransformerDecoder, TransformerDecoderLayer


class EncoderModel(nn.Module):
    def __init__(self, vocab_size, emb_size, hidden_dim_size, num_heads, num_layers, dropout=0.2, batch_first=True):
        super(EncoderModel, self).__init__()
        self.embedding = nn.Embedding(vocab_size, emb_size)
        encoder_layers = TransformerEncoderLayer(emb_size, num_heads, hidden_dim_size, dropout, batch_first=batch_first)
        self.transformer_encoder = TransformerEncoder(encoder_layers, num_layers)

    def forward(self, tokens, attention_mask):
        src = self.embedding(tokens * attention_mask)  # N, S, E
        output = self.transformer_encoder(src)
        return output


class DecoderModel(nn.Module):
    def __init__(self, vocab_size, emb_size, hidden_dim_size, num_heads, num_layers, dropout=0.2, batch_first=True):
        super(DecoderModel, self).__init__()
        self.embedding = nn.Embedding(vocab_size, emb_size)
        decoder_layers = nn.TransformerDecoderLayer(emb_size, num_heads, hidden_dim_size, batch_first=batch_first)
        self.transformer_decoder = nn.TransformerDecoder(decoder_layers, num_layers)
        self.linear = nn.Linear(emb_size, vocab_size)

    def forward(self, tokens, attention_mask, encoder_hidden_state):
        tgt = self.embedding(tokens)  # N, T, E
        out = self.transformer_decoder(tgt, encoder_hidden_state, memory_mask=attention_mask)
        logits = self.linear(out)
        return logits


def main():
    vocabulary_size = 10000
    embedding_size = 8
    hidden_dim_size = 16
    num_heads = 1
    num_layers = 1

    encoder = EncoderModel(vocabulary_size, embedding_size, hidden_dim_size, num_heads, num_layers)
    decoder = DecoderModel(vocabulary_size, embedding_size, hidden_dim_size, num_heads, num_layers)

    # Omit training - just export randomly initialized network

    tokens = torch.LongTensor([[1, 2, 3, 4, 5]])
    attention_mask = torch.LongTensor([[1, 1, 1, 1, 1]])

    torch.onnx.export(encoder,
                      (tokens, attention_mask),
                      "random_encoder.onnx",
                      input_names=["input_ids", "attention_mask"],
                      output_names=["last_hidden_state"],
                      dynamic_axes={
                          "input_ids": {0: "batch", 1: "tokens"},
                          "attention_mask": {0: "batch", 1: "tokens"},
                          "last_hidden_state": {0: "batch", 1: "tokens"},
                      },
                      opset_version=12)

    last_hidden_state = encoder.forward(tokens, attention_mask)
    tokens = torch.LongTensor([[0]])  #1, 2]])

    torch.onnx.export(decoder,
                      (tokens, attention_mask.float(), last_hidden_state),
                      "random_decoder.onnx",
                      input_names=["input_ids", "encoder_attention_mask", "encoder_hidden_states"],
                      output_names=["logits"],
                      dynamic_axes={
                          "input_ids": {0: "batch", 1: "target_tokens"},
                          "encoder_attention_mask": {0: "batch", 1: "source_tokens"},
                          "encoder_hidden_states": {0: "batch", 1: "source_tokens"},
                          "logits": {0: "batch", 1: "target_tokens"},
                      },
                      opset_version=12)


if __name__ == "__main__":
    main()


