// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import com.yahoo.collections.Pair;
import com.yahoo.text.Utf8;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

/**
 * The base class to do cross-language serialization and deserialization of complete object structures without
 * the need for a separate protocol. Each subclass needs to register itself using the {@link #registerClass(int, Class)}
 * method, and override {@link #onGetClassId()} to return the same classId as the one registered. Creating an instance
 * of an identifiable object is done through the {@link #create(Deserializer)} or {@link #createFromId(int)} factory
 * methods.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class Identifiable extends Selectable implements Cloneable {

    private static Registry registry = null;
    public static int classId = registerClass(1, Identifiable.class);

    /**
     * Returns the class identifier of this class. This proxies the {@link #onGetClassId()} method that must be
     * implemented by every subclass.
     *
     * @return the class identifier
     */
    public final int getClassId() {
        return onGetClassId();
    }

    /**
     * Returns the class identifier for which this class is registered. It is important that all subclasses match the
     * return value of this with their call to {@link #registerClass(int, Class)}.
     *
     * @return The class identifier.
     */
    protected int onGetClassId() {
        return classId;
    }

    /**
     * Serializes the content of this class into the given byte buffer. This method serializes its own identifier into
     * the buffer before invoking the {@link #serialize(Serializer)} method.
     *
     * @param buf The buffer to serialize to.
     * @return The buffer argument, to allow chaining.
     */
    public final Serializer serializeWithId(Serializer buf) {
        buf.putInt(null, getClassId());
        return serialize(buf);
    }

    /**
     * Serializes the content (excluding the identifier) of this class into the given byte buffer. If you need the
     * identifier serialized, use the {@link #serializeWithId(Serializer)} method instead. This method invokes the
     * {@link #onSerialize(Serializer)} method.
     *
     * @param buf The buffer to serialize to.
     * @return The buffer argument, to allow chaining.
     */
    public final Serializer serialize(Serializer buf) {
        onSerialize(buf);
        return buf;
    }

    /**
     * Serializes the content of this class into the given
     * buffer. This method must be implemented by all subclasses that
     * have content. If the subclass has no other content than the
     * semantics of its class type, this method does not need to be
     * overloaded.
     *
     * @param buf The buffer to serialize to.
     */
    protected void onSerialize(Serializer buf) {
        // empty
    }

    /**
     * Deserializes the content of this class from the given byte buffer. This method deserialize a class identifier
     * first, and asserts that this identifier matches the identifier of this class. This is usable if you have an
     * instance of a class whose content you wish to retrieve from a buffer.
     *
     * @param buf The buffer to deserialize from.
     * @return The buffer argument, to allow chaining.
     * @throws IllegalArgumentException Thrown if the deserialized class identifier does not match this class.
     */
    public final Deserializer deserializeWithId(Deserializer buf) {
        int id = buf.getInt(null);
        if (id != getClassId()) {
            Class<?> spec = registry.get(id);
            if (spec != null) {
                throw new IllegalArgumentException(
                        "Can not deserialize class '" + getClass().getName() + "' (id " + getClassId() + ") from " +
                        "buffer containing class '" + spec.getName() + "' (id " + id + ").");
            } else {
                throw new IllegalArgumentException(
                        "Can not deserialize class '" + getClass().getName() + "' (id " + getClassId() + ") from " +
                        "buffer containing unknown class id " + id + ".");
            }
        }
        return deserialize(buf);
    }

    /**
     * Deserializes the content (excluding the identifier) of this class from the given byte buffer. If you need the
     * identifier deserialized and verified, use the {@link #deserializeWithId(Deserializer)} method instead. This
     * method invokes the {@link #onDeserialize(Deserializer)} method.
     *
     * @param buf The buffer to deserialize from.
     * @return The buffer argument, to allow chaining.
     */
    public final Deserializer deserialize(Deserializer buf) {
        onDeserialize(buf);
        return buf;
    }

    /**
     * Deserializes the content of this class from the given byte
     * buffer. This method must be implemented by all subclasses that
     * have content. If the subclass has no other content than the
     * semantics of its class type, this method does not need to be
     * overloaded.
     *
     * @param buf The buffer to deserialize from.
     */
    protected void onDeserialize(Deserializer buf) {
        // empty
    }

    /**
     * Declares that all subclasses of Identifiable supports clone() by _not_ throwing CloneNotSupported exceptions.
     *
     * @return A cloned instance of this.
     * @throws AssertionError Thrown if a subclass does not implement clone().
     */
    @Override
    public Identifiable clone() {
        try {
            return (Identifiable)super.clone();
        } catch (CloneNotSupportedException e) {
            throw (AssertionError)new AssertionError("The cloneable structure has been broken.").initCause(e);
        }
    }

    @Override
    public int hashCode() {
        return getClassId();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Identifiable)) {
            return false;
        }
        Identifiable rhs = (Identifiable)obj;
        return (getClassId() == rhs.getClassId());
    }

    @Override
    public String toString() {
        ObjectDumper ret = new ObjectDumper();
        ret.visit("", this);
        return ret.toString();
    }

    /**
     * Registers the given class specification for the given identifier in the class registry. This method returns the
     * supplied identifier, so that subclasses can declare a static classId member like so:
     *
     * <code>public static int classId = registerClass(&lt;id&gt;, &lt;ClassName&gt;.class);</code>
     *
     * @param id   the class identifier to register with
     * @param spec the class to register
     * @return the identifier argument
     */
    protected static int registerClass(int id, Class<? extends Identifiable> spec) {
        if (registry == null) {
            registry = new Registry();
        }
        registry.add(id, spec);
        return id;
    }

    /**
     * Deserializes a single {@link Identifiable} object from the given byte buffer. The object itself may perform
     * recursive deserialization of {@link Identifiable} objects, but there is no requirement that this method consumes
     * the whole content of the buffer.
     *
     * @param buf The buffer to deserialize from.
     * @return The instantiated object.
     * @throws IllegalArgumentException Thrown if an unknown class is contained in the buffer.
     */
    public static Identifiable create(Deserializer buf) {
        int classId = buf.getInt(null);
        Identifiable obj = createFromId(classId);
        if (obj != null) {
            obj.deserialize(buf);
        } else {
            throw new IllegalArgumentException("Failed creating class for classId " + classId);
        }
        return obj;
    }

    /**
     * Creates an instance of the class registered with the given identifier. If the indentifier is unknown, this method
     * returns null.
     *
     * @param id The identifier of the class to instantiate.
     * @return The instantiated object.
     */
    public static Identifiable createFromId(int id) {
        return registry.createFromId(id);
    }

    /**
     * This is a convenience method to allow serialization of an optional field. A single byte is added to the buffer
     * indicating whether or not an object follows. If the object is not null, it is serialized following this flag.
     *
     * @param buf The buffer to serialize to.
     * @param obj The object to serialize, may be null.
     * @return The buffer, to allow chaining.
     */
    protected static Serializer serializeOptional(Serializer buf, Identifiable obj) {
        if (obj != null) {
            buf.putByte(null, (byte)1);
            obj.serializeWithId(buf);
        } else {
            buf.putByte(null, (byte)0);
        }
        return buf;
    }

    /**
     * This is a convenience method to allow deserialization of an optional field. See {@link
     * #serializeOptional(Serializer, Identifiable)} for notes on this.
     *
     * @param buf The buffer to deserialize from.
     * @return The instantiated object, or null.
     */
    protected static Identifiable deserializeOptional(Deserializer buf) {
        byte hasObject = buf.getByte(null);
        if (hasObject == 1) {
            return create(buf);
        }
        return null;
    }

    /**
     * Returns whether or not two objects are equal, taking into account that either can be null.
     *
     * @param lhs The left hand side of the comparison.
     * @param rhs The right hand side of the comparison.
     * @return True if both arguments are null or equal.
     */
    protected static boolean equals(Object lhs, Object rhs) {
        return !(lhs == null && rhs != null) &&
               !(lhs != null && rhs == null) &&
                ((lhs == null || lhs.equals(rhs)));
    }

    /**
     * This function needs to be implemented in such a way that it visits all its members. This is done by invoking the
     * {@link com.yahoo.vespa.objects.ObjectVisitor#visit(String, Object)} on the visitor argument for all members.
     *
     * @param visitor The visitor that is to access the member data.
     */
    public void visitMembers(ObjectVisitor visitor) {
        visitor.visit("classId", getClassId());
    }

    /**
     * This class implements the class registry used by {@link Identifiable} to allow for creation of classes from
     * shared class identifiers. It's methods are proxied through {@link Identifiable#registerClass(int, Class)}, {@link
     * Identifiable#createFromId(int)} and {@link Identifiable#create(Deserializer)}.
     */
    private static class Registry {

        // The map from class id to class descriptor.
        private HashMap<Integer, Pair<Class<? extends Identifiable>, Constructor<? extends Identifiable>>> typeMap =
                new HashMap<>();

        /**
         * Adds an entry in the type map, pairing the given identifier with the given class specification.
         *
         * @param id   The class identifier to register with.
         * @param spec The class to register.
         * @throws IllegalArgumentException Thrown if two classes attempt to register with the same identifier.
         */
        private void add(int id, Class<? extends Identifiable> spec) {
            Class<?> old = get(id);
            if (old == null) {
                Constructor<? extends Identifiable> constructor;
                try {
                    constructor = spec.getConstructor();
                } catch (NoSuchMethodException e) {
                    constructor = null;
                }
                typeMap.put(id, new Pair<Class<? extends Identifiable>, Constructor<? extends Identifiable>>(spec, constructor));
            } else if (!spec.equals(old)) {
                throw new IllegalArgumentException("Can not register class '" + spec.toString() + "' with id " + id +
                                                   ", because it already maps to class '" + old.toString() + "'.");
            }
        }

        /**
         * Returns the class registered for the given identifier.
         *
         * @param id The identifer whose class to return.
         * @return The class specification, may be null.
         */
        private Class<? extends Identifiable> get(int id) {
            Pair<Class<? extends Identifiable>, Constructor<? extends Identifiable>> pair = typeMap.get(id);
            return (pair != null) ? pair.getFirst() : null;
        }

        /**
         * Creates an instance of the class mapped to by the given identifier. This method proxies {@link
         * #createFromClass(Constructor)}.
         *
         * @param id The id of the class to create.
         * @return The instantiated object.
         */
        private Identifiable createFromId(int id) {
            Pair<Class<? extends Identifiable>, Constructor<? extends Identifiable>> pair = typeMap.get(id);
            return createFromClass((pair != null) ? pair.getSecond() : null);
        }

        /**
         * Creates an instance of a given class specification. All instantiation-type exceptions are consumed and
         * wrapped inside a runtime exception so that calling methods can let this propagate without declaring them
         * thrown.
         *
         * @param spec The class to instantiate.
         * @return The instantiated object.
         * @throws IllegalArgumentException Thrown if instantiation failed.
         */
        private Identifiable createFromClass(Constructor<? extends Identifiable> spec) {
            Identifiable obj = null;
            if (spec != null) {
                try {
                    obj = spec.newInstance();
                } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    throw new IllegalArgumentException("Failed to create object from class '" +
                                                       spec.getName() + "'.", e);
                }
            }
            return obj;
        }
    }

    protected static byte[] getRawUtf8Bytes(Deserializer buf) {
        int len = buf.getInt(null);
        return buf.getBytes(null, len);
    }

    protected String getUtf8(Deserializer buf) {
        return Utf8.toString(getRawUtf8Bytes(buf));
    }

    protected void putUtf8(Serializer buf, String val) {
        byte[] raw = Utf8.toBytes(val);
        buf.putInt(null, raw.length);
        buf.put(null, raw);
    }
}
