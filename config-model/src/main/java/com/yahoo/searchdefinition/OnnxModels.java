// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.vespa.model.AbstractService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ONNX models tied to a search definition or global.
 *
 * @author lesters
 */
public class OnnxModels {

    private final Map<String, OnnxModel> models = new HashMap<>();

    public void add(OnnxModel model) {
        model.validate();
        String name = model.getName();
        models.put(name, model);
    }

    public void add(Map<String, OnnxModel> models) {
        models.values().forEach(this::add);
    }

    public OnnxModel get(String name) {
        return models.get(name);
    }

    public boolean has(String name) {
        return models.containsKey(name);
    }

    public Map<String, OnnxModel> asMap() {
        return Collections.unmodifiableMap(models);
    }

    /** Initiate sending of these models to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        models.values().forEach(model -> model.sendTo(services));
    }

}
