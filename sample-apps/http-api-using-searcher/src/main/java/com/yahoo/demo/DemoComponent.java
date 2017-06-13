// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.demo;

import com.yahoo.component.AbstractComponent;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * A shared component with an "expensive" constructor exposing a shared,
 * thread-safe service.
 */
public class DemoComponent extends AbstractComponent {
    private final Set<Integer> illegalHashes;

    public DemoComponent() {
        illegalHashes = new HashSet<Integer>();
        Random r = new Random();
        // generate up to 1e6 unique hashes
        for (int i = 0; i < 1000 * 1000; ++i) {
            illegalHashes.add(r.nextInt());
        }
    }

    /**
     * NFKC-normalize term, or replace it with "smurf" with a low probability.
     * Will change choice for each run, but will be constant in a single run of
     * the container.
     *
     * @param term
     *            term to normalize or replace with "smurf"
     * @return NFKC-normalized term or "smurf"
     */
    public String normalize(String term) {
        String normalized = Normalizer.normalize(term, Normalizer.Form.NFKC);
        if (illegalHashes.contains(normalized.hashCode())) {
            return "smurf";
        } else {
            return normalized;
        }
    }

}
