## onnx-model

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile) or [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema). This defines a named ONNX model located in a file that can be used in ranking expressions using the "onnx" rank feature.

Prefer to define onnx models in the rank profiles using them. Onnx models are inherited from parent profiles, and from the schema.

```
onnx-model [name] {
    [body]
}
```

The body of an ONNX model must contain:

|      Name       |  Occurrence  |                                                                                                                                                      Description                                                                                                                                                      |
|-----------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| file            | One          | Path to the location of the file containing the ONNX model. The path is relative to the root of the application package containing this schema.                                                                                                                                                                       |
| input           | Zero to many | An input to the ONNX model. The ONNX name as given in the model as well as the source for the input is specified.                                                                                                                                                                                                     |
| output          | Zero to many | An output of the ONNX model. The ONNX name as given in the model as well as the name for use in Vespa is specified. If no output are defined and are not referred to from the rank feature, the first output defined in the model is used.                                                                            |
| gpu-device      | Zero or one  | Set the GPU device number to use for computation, starting at 0, i.e. if your GPU is `/dev/nvidia0` set this to 0. This must be an Nvidia CUDA-enabled GPU. Currently only models used in [global-phase](https://docs.vespa.ai/en/reference/schema-reference.html#globalphase-rank) can make use of GPU-acceleration. |
| intraop-threads | Zero or one  | The number of threads available for running operations with multithreaded implementations.                                                                                                                                                                                                                            |
| interop-threads | Zero or one  | The number of threads available for running multiple operations in parallel. This is only applicable for `parallel` execution mode.                                                                                                                                                                                   |
| execution-mode  | Zero or one  | Controls how the operators of a graph are executed, either `sequential` or `parallel`.                                                                                                                                                                                                                                |

For more details including examples, see [ranking with ONNX models.](https://docs.vespa.ai/en/onnx.html)
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#onnx-model)
