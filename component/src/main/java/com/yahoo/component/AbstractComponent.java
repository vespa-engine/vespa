// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import com.yahoo.collections.MethodCache;

import java.lang.reflect.Method;

/**
 * Superclass of destructible components. Container components to be created via dependency injection
 * do not have to extend this class unless they need to implement {@link #deconstruct}.
 *
 * @author bratseth
 */
public class AbstractComponent implements Component, Deconstructable {

    private static final MethodCache deconstructMethods = new MethodCache("deconstruct");

    // All accesses to id MUST go through getId.
    private ComponentId id;

    // We must store the class name, as this.getClass() will yield an exception when a bundled component's
    // bundle has been uninstalled.
    private final String className = getClass().getName();
    protected final boolean isDeconstructable;

    /**
     * Creates a new component which is invalid until {@link #initId} is called on it.
     * The dependency injection framework (DI) will always set the id, so components to be created
     * via DI do not have to implement other constructors, and should not set the id themselves.
     */
    protected AbstractComponent() {
        isDeconstructable = setIsDeconstructable();
    }

    /**
     * Creates a new component with an id.
     * Only for testing and components that are not created via dependency injection.
     *
     * @throws NullPointerException if the given id is null
     */
    protected AbstractComponent(ComponentId id) {
        initId(id);
        isDeconstructable = setIsDeconstructable();
    }

    /** Initializes this. Always called from a constructor or the framework. Do not call. */
    public final void initId(ComponentId id) {
        if (this.id != null && !this.id.equals(id))
            throw new RuntimeException("Can't change component id: " + this.id + " -> " + id);

        if (id==null) throw new NullPointerException("A component cannot be created with a null id");
        this.id=id;
    }

    /** Do NOT call at construction time. Returns the id of this component. */
    public final ComponentId getId() {
        if (id == null) {
            setTestId();
        }
        return id;
    }

    // This should only happen in tests, so thread safety should not be an issue.
    private void setTestId() {
        id = ComponentId.createAnonymousComponentId("test_" + getClass().getName());
    }

    /**
     * DO NOT CALL, for internal use only,
     */
    public final boolean hasInitializedId() {
        return id != null;
    }

    /**
     * DO NOT CALL, for internal use only,
     */
    public final String getIdString() {
        if (hasInitializedId())
            return getId().toString();

        return "(anonymous)";
    }

    public String getClassName() {
        return className;
    }

    @Override
    public String toString() {
        return "'" +  getIdString() + "' of class '" + className + "'";
    }

    /**
      * Clones this by returning a new instance <i>which does not have an id</i>.
       * An id can subsequently be assigned by calling {@link #initId}.
      * Note that even though this implements clone, the component subclass may
      * not in fact be clonable.
      *
      * @throws RuntimeException if the component is not clonable
      */
    @Override
    public AbstractComponent clone() {
        try {
            AbstractComponent clone=(AbstractComponent)super.clone();
            clone.id = null;
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("'" + this + "' is not clonable",e);
        }

    }

    /** Order by id order. It is permissible to change the order definition in subclasses */
    @Override
    public int compareTo(Component other) {
        return id.compareTo(other.getId());
    }

    /**
     * Implement this to perform any cleanup of structures or resources allocated in the constructor,
     * before this component is removed.
     * <p>
     * All other calls to this component is completed before this method is called.
     * It will only be called once. It should block while doing cleanup tasks and return when
     * this class is ready for garbage collection. This method is called in reverse dependency order,
     * so a component will be deconstructed after any other components it is injected into.
     * <p>
     * This default implementation does nothing.
     */
    @Override
    public void deconstruct() { }

    /**
     * @return true if this component has a non-default implementation of the {@link #deconstruct} method.
     */
    public final boolean isDeconstructable() {
        return isDeconstructable;
    }

    protected boolean setIsDeconstructable() {
        Method deconstruct = deconstructMethods.get(this);
        if (deconstruct == null) {
            com.yahoo.protect.Process.logAndDie("Component " + this + " does not have method deconstruct() - impossible!");
        }
        @SuppressWarnings("rawtypes")
        Class declaringClass = deconstruct.getDeclaringClass();
        return (declaringClass != AbstractComponent.class);
    }

}
