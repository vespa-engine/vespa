// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author baldersheim
 */
public class DataTypeRepo implements DataTypeCollection {

    private Map<Integer, DataType> typeById = new LinkedHashMap<>();
    private Map<String, DataType> typeByName = new LinkedHashMap<>();

    public DataType getDataType(String name) {
        return typeByName.get(name);
    }

    public DataType getDataType(int id) {
        return typeById.get(id);
    }

    public Collection<DataType> getTypes() { return typeById.values(); }

    public DataTypeRepo add(DataType type) {
        if (typeByName.containsKey(type.getName()) || typeById.containsKey(type.getId())) {
            throw new IllegalArgumentException("Data type '" + type.getName() + "', id '" +
                                               type.getId() + "' is already registered.");
        }
        typeByName.put(type.getName(), type);
        typeById.put(type.getId(), type);
        return this;
    }

    public DataTypeRepo addAll(DataTypeCollection repo) {
        for (DataType dataType : repo.getTypes()) {
            add(dataType);
        }
        return this;
    }

    public DataTypeRepo replace(DataType type) {
        if (!typeByName.containsKey(type.getName()) || !typeById.containsKey(type.getId())) {
            throw new IllegalStateException("Data type '" + type.getName() + "' is not registered.");
        }
        var oldByName = typeByName.remove(type.getName());
        var oldById = typeById.remove(type.getId());
        if (oldByName != oldById) {
            throw new IllegalStateException("Data type '" + type.getName() +
                                            "' inconsistent replace, by name: " + oldByName
                                            + " but by id: " + oldById);
        }
        typeByName.put(type.getName(), type);
        typeById.put(type.getId(), type);
        return this;
    }

}
