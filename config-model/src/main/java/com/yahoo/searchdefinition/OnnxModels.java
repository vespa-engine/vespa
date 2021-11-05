// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.FileRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ONNX models tied to a search definition or global.
 *
 * @author lesters
 */
public class OnnxModels {

    private final FileRegistry fileRegistry;

    /** The schema this belongs to, or empty if it is global */
    private final Optional<Schema> owner;

    private final Map<String, OnnxModel> models = new HashMap<>();

    public OnnxModels(FileRegistry fileRegistry, Optional<Schema> owner) {
        this.fileRegistry = fileRegistry;
        this.owner = owner;
    }

    public void add(OnnxModel model) {
        model.validate();
        model.register(fileRegistry);
        String name = model.getName();
        models.put(name, model);
    }

    public void add(Map<String, OnnxModel> models) {
        models.values().forEach(this::add);
    }

    public OnnxModel get(String name) {
        var model = models.get(name);
        if (model != null) return model;
        if (owner.isPresent() && owner.get().inherited().isPresent())
            return owner.get().inherited().get().onnxModels().get(name);
        return null;
    }

    public boolean has(String name) {
        boolean has = models.containsKey(name);
        if (has) return true;
        if (owner.isPresent() && owner.get().inherited().isPresent())
            return owner.get().inherited().get().onnxModels().has(name);
        return false;
    }

    public Map<String, OnnxModel> asMap() {
        // Shortcuts
        if (owner.isEmpty() || owner.get().inherited().isEmpty()) return Collections.unmodifiableMap(models);
        if (models.isEmpty()) return owner.get().inherited().get().onnxModels().asMap();

        var allModels = new HashMap<>(owner.get().inherited().get().onnxModels().asMap());
        allModels.putAll(models);
        return Collections.unmodifiableMap(allModels);
    }

}
