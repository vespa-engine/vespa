// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.language.process.Embedder;

import java.util.Set;

/**
 * A component in the config model which configures one of the embedders provided by Vespa.
 * The config model does not instantiate the embedder.
 * This interface declares the properties of the embedder which are known without an instance.
 *
 * @author glebashnik
 */
public interface EmbedderComponent {

    /** Returns the supported modalities of the embedder, as its {@link Embedder#supportedModalities()} does. */
    default Set<Set<Embedder.Modality>> supportedModalities() { return Set.of(Set.of(Embedder.Modality.TEXT)); }

}
