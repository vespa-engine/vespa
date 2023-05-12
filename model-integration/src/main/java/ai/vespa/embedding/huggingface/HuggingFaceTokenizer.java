// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.embedding.huggingface;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Wrapper around Deep Java Library's HuggingFace tokenizer.
 *
 * @author bjorncs
 */
public class HuggingFaceTokenizer implements AutoCloseable {

    private final ai.djl.huggingface.tokenizers.HuggingFaceTokenizer instance;

    public HuggingFaceTokenizer(Path path) throws IOException { this(path, HuggingFaceTokenizerOptions.defaults()); }

    public HuggingFaceTokenizer(Path path, HuggingFaceTokenizerOptions opts) throws IOException {
        var original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(HuggingFaceTokenizer.class.getClassLoader());
        try {
            instance = createInstance(path, opts);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public Encoding encode(String text) { return Encoding.from(instance.encode(text)); }

    @Override public void close() { instance.close(); }

    private static ai.djl.huggingface.tokenizers.HuggingFaceTokenizer createInstance(
            Path path, HuggingFaceTokenizerOptions opts) throws IOException {
        var builder = ai.djl.huggingface.tokenizers.HuggingFaceTokenizer.builder().optTokenizerPath(path);
        opts.addSpecialToken().ifPresent(builder::optAddSpecialTokens);
        opts.truncation().ifPresent(builder::optTruncation);
        if (opts.truncateFirstOnly()) builder.optTruncateFirstOnly();
        if (opts.truncateSecondOnly()) builder.optTruncateSecondOnly();
        opts.padding().ifPresent(builder::optPadding);
        if (opts.padToMaxLength()) builder.optPadToMaxLength();
        opts.maxLength().ifPresent(builder::optMaxLength);
        opts.padToMultipleOf().ifPresent(builder::optPadToMultipleOf);
        opts.stride().ifPresent(builder::optStride);
        return builder.build();
    }
}
