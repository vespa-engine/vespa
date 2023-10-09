# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import torch
import torch.onnx


class MyModel(torch.nn.Module):
    def __init__(self):
        super(MyModel, self).__init__()
        self.linear = torch.nn.Linear(in_features=3, out_features=1)
        self.logistic = torch.nn.Sigmoid()

    def forward(self, vec):
        return self.logistic(self.linear(vec))


def main():
    model = MyModel()

    # Omit training - just export randomly initialized network

    data = torch.FloatTensor([[0.1, 0.2, 0.3],[0.4, 0.5, 0.6]])
    torch.onnx.export(model,
                      data,
                      "one_layer.onnx",
                      input_names = ["input"],
                      output_names = ["output"],
                      dynamic_axes = {
                          "input": {0: "batch"},
                          "output": {0: "batch"},
                      },
                      opset_version=12)


if __name__ == "__main__":
    main()


