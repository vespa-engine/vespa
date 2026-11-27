// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.language.process.Embedder;

import java.util.Set;

/**
 * A component in the config model which configures one of the built-in embedders.
 * The config model does not instantiate the embedder.
 * This interface declares the properties of the embedder which are known without an instance.
 *
 * @author glebashnik
 */
public interface EmbedderComponent {

    /** Must equal {@link Embedder#supportedModalities()} of the embedder. */
    default Set<Set<Embedder.Modality>> supportedModalities() { return Set.of(Set.of(Embedder.Modality.TEXT)); }

}
