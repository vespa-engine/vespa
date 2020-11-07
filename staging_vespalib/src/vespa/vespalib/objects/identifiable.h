// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
/**
 * \class vespalib::Identifiable
 * \ingroup util
 *
 * \brief Superclass for objects adding some runtime type information.
 *
 * This class is a superclass used by many other classes to add some runtime
 * information.
 *
 * This can be used to verify type to be able to do cheap static casts
 * instead of dynamic casts. It can be used to identify type of object, and
 * it can also be used to generate an object of the given type (If it's not
 * an identifiable abstract type).
 *
 */

#define CID_Identifiable 1

#include "ids.h"
#include "nboserializer.h"
#include "objectvisitor.h"

#include <vespa/vespalib/util/memory.h>

#define IDENTIFIABLE_CLASSID(cclass) CID_##cclass
#define IDENTIFIABLE_CLASSID_NS(ns, cclass) CID_##ns##_##cclass
#define IDENTIFIABLE_CLASSID_NS2(ns1, ns2, cclass) CID_##ns1##_##ns2##_##cclass
#define IDENTIFIABLE_CLASSID_NS3(ns1, ns2, ns3, cclass) CID_##ns1##_##ns2##_##ns3##_##cclass

#define DECLARE_IDENTIFIABLE_STATIC_BASE_COMMON(cclass) \
  static  vespalib::Identifiable::RuntimeInfo  _RTInfo; \
  static  vespalib::Identifiable::RuntimeClass _RTClass; \
  static const std::type_info & typeId() { return typeid(cclass); } \
  static bool tryCast(const vespalib::Identifiable * v) { return dynamic_cast<const cclass *>(v) != NULL; } \
  static cclass *identifyClassAsIdentifiable() { return NULL; } /* no implementation */

#define DECLARE_IDENTIFIABLE_BASE_COMMON(cclass) \
  DECLARE_IDENTIFIABLE_STATIC_BASE_COMMON(cclass) \
  const vespalib::Identifiable::RuntimeClass & getClass() const override;

#define DECLARE_IDENTIFIABLE_BASE_COMMON_ROOT(cclass) \
  DECLARE_IDENTIFIABLE_STATIC_BASE_COMMON(cclass) \
  virtual const vespalib::Identifiable::RuntimeClass & getClass() const;

#define DECLARE_IDENTIFIABLE_BASE(cclass, classid) \
  public:                                          \
   enum { classId=classid };                       \
  DECLARE_IDENTIFIABLE_BASE_COMMON(cclass)

#define DECLARE_IDENTIFIABLE_BASE_ROOT(cclass, classid) \
  public:                                          \
   enum { classId=classid };                       \
  DECLARE_IDENTIFIABLE_BASE_COMMON_ROOT(cclass)

#define DECLARE_IDENTIFIABLE_ABSTRACT(cclass)               DECLARE_IDENTIFIABLE_BASE(cclass, IDENTIFIABLE_CLASSID(cclass))
#define DECLARE_IDENTIFIABLE_ABSTRACT_NS(ns, cclass)        DECLARE_IDENTIFIABLE_BASE(ns::cclass, IDENTIFIABLE_CLASSID_NS(ns, cclass))
#define DECLARE_IDENTIFIABLE_ABSTRACT_NS2(ns1, ns2, cclass) DECLARE_IDENTIFIABLE_BASE(ns1::ns2::cclass, IDENTIFIABLE_CLASSID_NS2(ns1, ns2, cclass))
#define DECLARE_IDENTIFIABLE_ABSTRACT_NS3(ns1, ns2, ns3, cclass) DECLARE_IDENTIFIABLE_BASE(ns1::ns2::ns3::cclass, IDENTIFIABLE_CLASSID_NS3(ns1, ns2, ns3, cclass))

#define DECLARE_IDENTIFIABLE_STATIC_COMMON(cclass)                               \
  static cclass *       create()               { return new cclass(); }          \
  static Identifiable * createAsIdentifiable() { return cclass::create(); }

#define DECLARE_IDENTIFIABLE_COMMON(cclass)                  \
  void assign(const vespalib::Identifiable & rhs) override;  \
  DECLARE_IDENTIFIABLE_STATIC_COMMON(cclass)

#define DECLARE_IDENTIFIABLE_COMMON_ROOT(cclass)            \
  virtual void assign(const vespalib::Identifiable & rhs);  \
  DECLARE_IDENTIFIABLE_STATIC_COMMON(cclass)

#define DECLARE_IDENTIFIABLE(cclass)                                             \
  DECLARE_IDENTIFIABLE_BASE(cclass, IDENTIFIABLE_CLASSID(cclass))                \
  DECLARE_IDENTIFIABLE_COMMON(cclass)

#define DECLARE_IDENTIFIABLE_ROOT(cclass)                                             \
  DECLARE_IDENTIFIABLE_BASE_ROOT(cclass, IDENTIFIABLE_CLASSID(cclass))                \
  DECLARE_IDENTIFIABLE_COMMON_ROOT(cclass)

#define DECLARE_IDENTIFIABLE_NS(ns, cclass)                                      \
  DECLARE_IDENTIFIABLE_BASE(ns::cclass, IDENTIFIABLE_CLASSID_NS(ns, cclass))     \
  DECLARE_IDENTIFIABLE_COMMON(ns::cclass)

#define DECLARE_IDENTIFIABLE_NS2(ns1, ns2, cclass)                               \
  DECLARE_IDENTIFIABLE_BASE(ns1::ns2::cclass, IDENTIFIABLE_CLASSID_NS2(ns1, ns2, cclass))            \
  DECLARE_IDENTIFIABLE_COMMON(ns1::ns2::cclass)

#define DECLARE_IDENTIFIABLE_NS3(ns1, ns2, ns3, cclass)                          \
  DECLARE_IDENTIFIABLE_BASE(ns1::ns2::ns3::cclass, IDENTIFIABLE_CLASSID_NS3(ns1, ns2, ns3, cclass))   \
  DECLARE_IDENTIFIABLE_COMMON(ns1::ns2::ns3::cclass)

#define  IMPLEMENT_IDENTIFIABLE_COMMON(cclass) \
  vespalib::Identifiable::RuntimeClass cclass::_RTClass(&_RTInfo);                                                     \
  const vespalib::Identifiable::RuntimeClass & cclass::getClass() const { return _RTClass; }

#define  IMPLEMENT_IDENTIFIABLE_CONCRET(cclass)               \
  void cclass::assign(const vespalib::Identifiable & rhs) {   \
      if (rhs.inherits(classId)) {                            \
          *this = static_cast<const cclass &>(rhs);           \
      }                                                       \
   }

#define IMPLEMENT_IDENTIFIABLE_BASE(cclass, name, base, id, factory, ctypeInfo, tryCast, typeinfo)                                \
  vespalib::Identifiable::RuntimeInfo  cclass::_RTInfo = {name, typeinfo, id, factory, ctypeInfo, tryCast, &base::_RTInfo }; \
  IMPLEMENT_IDENTIFIABLE_COMMON(cclass)

#define IMPLEMENT_IDENTIFIABLE_ABSTRACT(cclass, base)  \
  IMPLEMENT_IDENTIFIABLE_BASE(cclass, #cclass, base, IDENTIFIABLE_CLASSID(cclass), \
                              NULL, cclass::typeId, cclass::tryCast, "")
#define IMPLEMENT_IDENTIFIABLE(cclass, base) \
  IMPLEMENT_IDENTIFIABLE_CONCRET(cclass)           \
  IMPLEMENT_IDENTIFIABLE_BASE(cclass, #cclass, base, IDENTIFIABLE_CLASSID(cclass), \
                              cclass::createAsIdentifiable, cclass::typeId, cclass::tryCast, "")
#define IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS(ns, cclass, base) \
  IMPLEMENT_IDENTIFIABLE_BASE(ns::cclass, #ns"::"#cclass, base, IDENTIFIABLE_CLASSID_NS(ns, cclass), \
                              NULL, cclass::typeId, cclass::tryCast, "")
#define IMPLEMENT_IDENTIFIABLE_NS(ns, cclass, base) \
  IMPLEMENT_IDENTIFIABLE_CONCRET(ns::cclass)           \
  IMPLEMENT_IDENTIFIABLE_BASE(ns::cclass, #ns"::"#cclass, base, IDENTIFIABLE_CLASSID_NS(ns, cclass), \
                              cclass::createAsIdentifiable, cclass::typeId, cclass::tryCast, "")
#define IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(ns1, ns2, cclass, base) \
  IMPLEMENT_IDENTIFIABLE_BASE(ns1::ns2::cclass, #ns1"::"#ns2"::"#cclass, base, IDENTIFIABLE_CLASSID_NS2(ns1, ns2, cclass), \
                              NULL, cclass::typeId, cclass::tryCast, "")
#define IMPLEMENT_IDENTIFIABLE_NS2(ns1, ns2, cclass, base) \
  IMPLEMENT_IDENTIFIABLE_CONCRET(ns1::ns2::cclass)           \
  IMPLEMENT_IDENTIFIABLE_BASE(ns1::ns2::cclass, #ns1"::"#ns2"::"#cclass, base, IDENTIFIABLE_CLASSID_NS2(ns1, ns2, cclass), \
                              cclass::createAsIdentifiable, cclass::typeId, cclass::tryCast, "")
#define IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS3(ns1, ns2, ns3, cclass, base) \
  IMPLEMENT_IDENTIFIABLE_BASE(ns1::ns2::ns3::cclass, #ns1"::"#ns2"::"#ns3"::"#cclass, base, IDENTIFIABLE_CLASSID_NS3(ns1, ns2, ns3, cclass), \
                              NULL, cclass::typeId, cclass::tryCast, "")
#define IMPLEMENT_IDENTIFIABLE_NS3(ns1, ns2, ns3, cclass, base) \
  IMPLEMENT_IDENTIFIABLE_CONCRET(ns1::ns2::ns3::cclass)           \
  IMPLEMENT_IDENTIFIABLE_BASE(ns1::ns2::ns3::cclass, #ns1"::"#ns2"::"#ns3"::"#cclass, base, IDENTIFIABLE_CLASSID_NS3(ns1, ns2, ns3, cclass), \
                              cclass::createAsIdentifiable, cclass::typeId, cclass::tryCast, "")

#define DECLARE_NBO_SERIALIZE                            \
    vespalib::Serializer & onSerialize(vespalib::Serializer & os) const override; \
    vespalib::Deserializer & onDeserialize(vespalib::Deserializer & is) override;


namespace vespalib {

class ObjectPredicate;
class ObjectOperation;

class Identifiable {
 protected:
    struct  RuntimeInfo {
        const char              * _name;
        const char              * _info;
        unsigned                  _id;
        Identifiable         * (* _factory)();
        const std::type_info & (* _typeId)();
        bool                   (* _tryCast)(const Identifiable *);
      const RuntimeInfo       * _base;
    };
public:
    typedef std::unique_ptr<Identifiable> UP;
    class ILoader
    {
    public:
        virtual ~ILoader() { }
        virtual bool hasClass(unsigned classId) const = 0;
        virtual bool hasClass(const char * className) const = 0;
        virtual void loadClass(unsigned classId) = 0;
        virtual void loadClass(const char * className) = 0;
    };
    struct RuntimeClass {
    public:
        RuntimeClass(RuntimeInfo * info);
        ~RuntimeClass();
        const char *         name()     const { return _rt->_name; }
        const char *         info()     const { return _rt->_info; }
        unsigned               id()     const { return _rt->_id; }
        Identifiable *     create()     const { return _rt->_factory ? _rt->_factory() : 0; }
        const std::type_info & typeId() const { return _rt->_typeId(); }
        bool tryCast(const Identifiable *o) const { return _rt->_tryCast(o); }
        const RuntimeInfo *  base()     const { return _rt->_base; }
        bool inherits(unsigned id) const;
        bool equal(unsigned cid) const { return id() == cid; }
        int compare(const RuntimeClass& other) const { return (id() - other.id()); }
    private:
        RuntimeInfo * _rt;
    };
    DECLARE_IDENTIFIABLE_ROOT(Identifiable);
    Identifiable() noexcept = default;
    Identifiable(Identifiable &&) noexcept = default;
    Identifiable & operator = (Identifiable &&) noexcept = default;
    Identifiable(const Identifiable &) = default;
    Identifiable & operator = (const Identifiable &) = default;
    virtual ~Identifiable() noexcept = default;

    /**
     * Will produce the full demangled className
     */
    string getNativeClassName() const;

    /**
     * This returns the innermost class that you represent. Default is that that is yourself.
     * However when you are a vector containing other objects, it might be feasible
     * to let the world know about them too.
     * @return the class info for the innermost object.
     */
    virtual const RuntimeClass & getBaseClass() const { return getClass(); }
    /**
     * Checks if this object inherits from a class with the given id.
     * @param id The id of the class to check if is an anchestor.
     * @return true if the object does inherit from it. Significantly faster than using dynamic cast.
     */
    bool inherits(unsigned id) const { return getClass().inherits(id); }
    /**
     * Checks if this object inherits from a class with the given name.
     * @param name The name of the class to check if is an anchestor.
     * @return true if the object does inherit from it. Significantly faster than using dynamic cast.
     */
    bool inherits(const char * name) const;
    /**
     * Identifiable::cast<T> behaves like dynamic_cast<T> when trying
     * to cast between Identifiable objects, using the inherits()
     * function defined above to check if the cast should succeed.
     */
    template <typename T> struct BaseType { typedef T type; };
    template <typename T> struct BaseType<T &> { typedef T type; };
    template <typename T> struct BaseType<T *> { typedef T type; };
    template <typename T> struct BaseType<const T &> { typedef T type; };
    template <typename T> struct BaseType<const T *> { typedef T type; };
    template <typename Type> static void ERROR_Type_is_not_Identifiable() {
        Type *(*foo)() = &Type::identifyClassAsIdentifiable;
        (void) foo;
    }
    template <typename T, typename Base>
    static T cast(Base &p) {
        typedef typename BaseType<T>::type Type;
        ERROR_Type_is_not_Identifiable<Type>();  // Help diagnose errors.
        if (p.inherits(Type::classId)) { return static_cast<T>(p); }
        else { throw std::bad_cast(); }
    }
    template <typename T, typename Base>
    static T cast(Base *p) {
        typedef typename BaseType<T>::type Type;
        ERROR_Type_is_not_Identifiable<Type>();  // Help diagnose errors.
        if (p && p->inherits(Type::classId)) { return static_cast<T>(p); }
        else { return 0; }
    }
    /**
     * Given the unique registered id of a class it will look up the object describing it.
     * @return object describing the class.
     */
    static const RuntimeClass * classFromId(unsigned id);
    /**
     * Given the unique registered name of a class it will look up the object describing it.
     * @return object describing the class.
     */
    static const RuntimeClass * classFromName(const char * name);
    /**
     * Here you can provide an optional classloader.
     */
    static void registerClassLoader(ILoader & loader) { _classLoader = &loader; }
    static void clearClassLoader() { _classLoader = NULL; }

    /**
     * Create a human-readable representation of this object. This
     * method will use object visitation internally to capture the
     * full structure of this object.
     *
     * @return structured human-readable representation of this object
     **/
    string asString() const;

    /**
     * Visit each of the members of this object. This method should be
     * overridden by subclasses and should present all appropriate
     * internal structure of this object to the given visitor. Note
     * that while each level of a class hierarchy may cooperate to
     * visit all object members (invoking superclass method within
     * method), this method, as implemented in the Identifiable class
     * should not be invoked, since its default implementation is
     * there to signal about the method not being overridden.
     *
     * @param visitor the visitor of this object
     **/
    virtual void visitMembers(ObjectVisitor &visitor) const;

    /**
     * Compares 2 objects. First tests classId, then lives it to the objects  and onCmp
     * if classes are the same.
     * @param b the object to compare with.
     * @return <0 if this comes before b, 0 for equality, and >0 fi b comes before *this.
     */
    int cmp(const Identifiable& b) const {
        int r(cmpClassId(b));
        return (r==0) ? onCmp(b) : r;
    }
    /**
     * Compares 2 objects. This is faster method that cmp as classId tests is not done.
     * onCmp is called directly.
     * @param b the object to compare with.
     * @return <0 if this comes before b, 0 for equality, and >0 fi b comes before *this.
     */
    int cmpFast(const Identifiable& b) const { return onCmp(b); }

    /**
     * Apply the predicate to this object. If the predicate returns
     * true, pass this object to the operation, otherwise invoke the
     * @ref selectMemebers method to locate sub-elements that might
     * trigger the predicate.
     *
     * @param predicate component used to select (sub-)objects
     * @param operation component performing some operation
     *                  on the selected (sub-)objects
     **/
    void select(const ObjectPredicate &predicate, ObjectOperation &operation);

    /**
     * Invoke @ref select on any member objects this object wants to
     * expose through the selection mechanism. Overriding this method
     * is optional, and which objects to expose is determined by the
     * application logic of the object itself.
     *
     * @param predicate component used to select (sub-)objects
     * @param operation component performing some operation
     *                  on the selected (sub-)objects
     **/
    virtual void selectMembers(const ObjectPredicate &predicate,
                               ObjectOperation &operation);

    /**
     * This will serialize the object by calling the virtual onSerialize.
     * The id of the object is not serialized.
     * @param os The nbostream used for output.
     * @return The nbostream after serialization.
     */
    Serializer & serialize(Serializer & os) const;
    /**
     * This will deserialize the object by calling the virtual onDeserialize.
     * It is symetric with serialize.
     * @param is The nbostream used for input.
     * @return The nbostream after deserialization.
     */
    Deserializer & deserialize(Deserializer & is);
    /**
     * This will read the first 4 bytes containing the classid. It will then create the
     * correct object based on that class, and then call deserialize with the rest.
     * It is symetric with the << operator, and does the same as >>, except instead of checking the id
     * it uses it to construct an object.
     * @param is The nbostream used for input.
     * @return The object created and constructed by deserialize. NULL if there are any errors.
     */
    static UP create(Deserializer & is);
    static UP create(nbostream & is) { NBOSerializer nis(is); return create(nis); }
    /**
     * This will serialize the object by first serializing the 4 byte classid.
     * Then the rest is serialized by calling serialize.
     * @param os The nbostream used for output.
     * @param obj The object to serialize.
     * @return The nbostream after serialization.
     */
    friend Serializer & operator << (Serializer & os, const Identifiable & obj);
    friend nbostream & operator << (nbostream & os, const Identifiable & obj);
    /**
     * This will read the first 4 bytes containing the classid. It will then check if it matches its own.
     * if not it will mark the nbostream as bad. Then it will deserialize he content.
     * It is symetric with the << operator
     * @param is The nbostream used for input.
     * @param obj The object that the stream will be deserialized into.
     * @return The object created and constructed by deserialize. NULL if there are any errors.
     */
    friend Deserializer & operator >> (Deserializer & os, Identifiable & obj);
    friend nbostream & operator >> (nbostream & os, Identifiable & obj);

    /**
     * This will serialize a 4 byte num element before it will serialize all elements.
     * This is used when you have a vector of objects of known class.
     * It is symetric with deserialize template below
     * @param v is vector of non polymorf Identifiable.
     * @param os is output stream
     * @return outputstream
     */
    template <typename T>
    static Serializer & serialize(const T & v, Serializer & os);

    /**
     * This will serialize a 4 byte num element before it will serialize all elements.
     * This is used when you have a vector of objects of known class.
     * It is symetric with deserialize template below
     * @param v is vector of non polymorf Identifiable.
     * @param os is output stream
     * @return outputstream
     */
    template <typename T>
    static Deserializer & deserialize(T & v, Deserializer & is);

    Serializer & serializeDirect(Serializer & os) const { return onSerialize(os); }
    Deserializer & deserializeDirect(Deserializer & is)  { return onDeserialize(is); }
protected:
    /**
     * Returns the diff of the classid. Used for comparing classes.
     * @param b the object to compare with.
     * @return getClass().id() - b.getClass().id()
     */
    int cmpClassId(const Identifiable& b) const
    { return getClass().id() - b.getClass().id(); }

private:
    /**
     * Interface for comparing objects to each other. Default implementation
     * is to serialise the two objects and use memcmp to verify equality.
     * Classes should overide this method if they have special needs.
     * It might also be an idea to override for speed, as serialize is 'expensive'.
     * @param b the object to compare with.
     * @return <0 if this comes before b, 0 for equality, and >0 fi b comes before *this.
     */
    virtual int onCmp(const Identifiable& b) const;
    virtual Serializer & onSerialize(Serializer & os) const;
    virtual Deserializer & onDeserialize(Deserializer & is);

    static ILoader  * _classLoader;
};

}

