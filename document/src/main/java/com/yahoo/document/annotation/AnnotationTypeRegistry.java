// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A registry of annotation types.&nbsp;This can be set up programmatically or from config.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class AnnotationTypeRegistry {
    private Map<Integer, AnnotationType> idMap = new HashMap<Integer, AnnotationType>();
    private Map<String, AnnotationType> nameMap = new HashMap<String, AnnotationType>();

    /** Creates a new empty registry. */
    public AnnotationTypeRegistry() {
    }

    /**
     * Register a new annotation type.&nbsp;WARNING!&nbsp;Only to be used by the configuration system and in unit tests.&nbsp;Not to be used in production code.
     *
     * @param type the type to register
     * @throws IllegalArgumentException if a type is already registered with this name or this id, and it is non-equal to the argument.
     */
    public void register(AnnotationType type) {
        if (idMap.containsKey(type.getId()) || nameMap.containsKey(type.getName())) {
            AnnotationType idType = idMap.get(type.getId());
            AnnotationType nameType = nameMap.get(type.getName());
            if (type.equals(idType) && type.equals(nameType)) {
                //it's the same one being re-registered, we're OK!
                return;
            }
            throw new IllegalArgumentException("A type is already registered with this name or this id.");
        }
        idMap.put(type.getId(), type);
        nameMap.put(type.getName(), type);
    }

    /**
     * Unregisters the type given by the argument.&nbsp;WARNING!&nbsp;Only to be used by the configuration system and in unit tests.&nbsp;Not to be used in production code.
     *
     * @param name the name of the type to unregister.
     * @return true if the type was successfully unregistered, false otherwise (it was not present)
     */
    public boolean unregister(String name) {
        AnnotationType oldType = nameMap.remove(name);
        if (oldType != null) {
            idMap.remove(oldType.getId());
            return true;
        }
        return false;
    }

    /**
     * Unregisters the type given by the argument.&nbsp;WARNING!&nbsp;Only to be used by the configuration system and in unit tests.&nbsp;Not to be used in production code.
     *
     * @param id the id of the type to unregister.
     * @return true if the type was successfully unregistered, false otherwise (it was not present)
     */
    public boolean unregister(int id) {
        AnnotationType oldType = idMap.remove(id);
        if (oldType != null) {
            nameMap.remove(oldType.getName());
            return true;
        }
        return false;
    }

    /**
     * Unregisters the type given by the argument.&nbsp;WARNING!&nbsp;Only to be used by the configuration system and in unit tests.&nbsp;Not to be used in production code.
     *
     * @param type the AnnotationType to unregister.
     * @return true if the type was successfully unregistered, false otherwise (it was not present)
     * @throws IllegalArgumentException if the ID and name of this annotation type are present, but they do not belong together.
     */
    public boolean unregister(AnnotationType type) {
        if (idMap.containsKey(type.getId()) && nameMap.containsKey(type.getName())) {
            AnnotationType idType = idMap.get(type.getId());
            AnnotationType nameType = nameMap.get(type.getName());

            if (idType == nameType) {
                //name and id belong together in our maps
                idMap.remove(type.getId());
                nameMap.remove(type.getName());
            } else {
                throw new IllegalArgumentException("The ID and name of this annotation type are present, but they do not belong together. Not removing type.");
            }
            return true;
        }
        //it's not there, but that's no problem
        return false;
    }

    /**
     * Returns an annotation type with the given name.
     *
     * @param name the name of the annotation type to return
     * @return an {@link AnnotationType} with the given name, or null if it is not registered
     */
    public AnnotationType getType(String name) {
        return nameMap.get(name);
    }

    /**
     * Returns an annotation type with the given id.
     *
     * @param id the id of the annotation type to return
     * @return an {@link AnnotationType} with the given id, or null if it is not registered
     */
    public AnnotationType getType(int id) {
        return idMap.get(id);
    }

    /**
     * Returns an <strong>unmodifiable</strong> {@link Map} of all types registered.
     *
     * @return an unmodifiable {@link Map} of all types registered.
     */
    public Map<String, AnnotationType> getTypes() {
        return Collections.unmodifiableMap(nameMap);
    }

    /** Clears all registered annotation types. */
    public void clear() {
        idMap.clear();
        nameMap.clear();
    }
}
