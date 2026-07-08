// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.yahoo.language.process.Embedder;

import java.util.List;

/**
 * Prepends a query- or document-specific prefix to embedder input text based on
 * {@link com.yahoo.language.process.InvocationContext.DestinationType}.
 *
 * @author bjorncs
 */
public record TextPrepender(String prependQuery, String prependDocument) {

    public String prepend(String text, Embedder.Context context) {
        String prefix = prefixFor(context);
        return prefix == null ? text : prefix + text;
    }

    /** Returns {@code texts} unchanged when no prefix applies — avoids allocation in the common case. */
    public List<String> prependAll(List<String> texts, Embedder.Context context) {
        String prefix = prefixFor(context);
        if (prefix == null) return texts;
        return texts.stream().map(t -> prefix + t).toList();
    }

    private String prefixFor(Embedder.Context context) {
        String prefix = context.getDestinationType() == Embedder.Context.DestinationType.QUERY
                ? prependQuery : prependDocument;
        return (prefix == null || prefix.isBlank()) ? null : prefix;
    }
}
