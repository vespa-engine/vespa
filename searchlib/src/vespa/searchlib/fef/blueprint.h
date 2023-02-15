// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "featureexecutor.h"
#include "iindexenvironment.h"
#include "iqueryenvironment.h"
#include "idumpfeaturevisitor.h"
#include "parameter.h"
#include "parameterdescriptions.h"
#include "feature_type.h"
#include <optional>

namespace vespalib { class Stash; }

namespace search::fef {

/**
 * A blueprint is a description of a named feature executor with a
 * given set of parameters that also acts as a factory for that
 * feature executor. During setup, the blueprint will look at the
 * parameters and generate a list of input feature names and also name
 * and describe its outputs. A blueprint will be created per rank
 * setup and used to create feature executors per query. A single
 * instance is used as a prototype to create actual blueprints used by
 * the framework. The prototype instance will also get a chance to
 * name features that should be dumped when doing a full feature dump
 * (feature dumps are used for things like MLR training). It will be
 * possible to define additional dump features in the config.
 **/
class Blueprint
{
public:
    /**
     * A feature can be either a number (double) or an object
     * (vespalib::eval::Value::CREF). This enum is used to describe
     * the accepted type for a specific input to a feature executor.
     **/
    enum class AcceptInput { NUMBER, OBJECT, ANY };

    /**
     * Interface used to set up feature dependencies recursively. This
     * is needed to know the exact type of an input feature during
     * executor setup.
     **/
    struct DependencyHandler {
        virtual std::optional<FeatureType> resolve_input(const vespalib::string &feature_name, AcceptInput accept_type) = 0;
        virtual void define_output(const vespalib::string &output_name, FeatureType type) = 0;
        virtual void fail(const vespalib::string &msg) = 0;
        virtual ~DependencyHandler() = default;
    };

    /**
     * Convenience typedef for an auto pointer to this class.
     **/
    using UP = std::unique_ptr<Blueprint>;

    /**
     * Convenience typedef for an shared pointer to this class.
     **/
    using SP = std::shared_ptr<Blueprint>;

    using string = vespalib::string;
    using StringVector = std::vector<string>;

private:
    string                   _baseName;
    string                   _name;
    DependencyHandler       *_dependency_handler;

protected:
    /**
     * Create an empty blueprint. Blueprints in their initial state
     * are used as prototypes to create other instances of the same
     * class. The @ref setup method is used to tailor a blueprint
     * object for a specific set of parameters.
     **/
    explicit Blueprint(vespalib::stringref baseName);

    using IAttributeVector = attribute::IAttributeVector;
    /**
     * Define an input feature for this blueprint. This method should
     * be invoked by the @ref setup method. Note that the order in
     * which the inputs are defined is extremely important, since this
     * must exactly match the input order of the corresponding feature
     * executor. Note that inputs must be addressed with full feature
     * names, for example 'foo(a,b).out'.
     *
     * @param inName feature name of input
     * @param type accepted input type
     **/
    std::optional<FeatureType> defineInput(vespalib::stringref inName,
                                           AcceptInput accept = AcceptInput::NUMBER);

    /**
     * Describe an output for this blueprint. This method should be
     * invoked by the @ref setup method. Note that the order in which
     * the outputs are described is extremely important, since this
     * must exactly match the output order of the corresponding
     * feature executor. Note that the output name is local to this
     * blueprint. As an example, the blueprint 'foo(a,b)' having the
     * feature 'foo(a,b).out' as output, would describe it simply as
     * 'out'.
     *
     * @param outName output name
     * @param desc output description
     **/
    void describeOutput(vespalib::stringref outName, vespalib::stringref desc,
                        FeatureType type = FeatureType::number());

    /**
     * Fail the setup of this blueprint with the given message. This
     * function should be called by the @ref setup function when it
     * fails. The failure is handled by the dependency handler to make
     * sure we only report the first error for each feature.
     *
     * @return false
     * @param format printf-style format string
     **/
    bool fail(const char *format, ...) __attribute__ ((format (printf,2,3)));

    /**
     * Used to store a reference to the attribute during prepareSharedState
     * for later use in createExecutor
     **/
    static const IAttributeVector *
    lookupAndStoreAttribute(const vespalib::string & key, vespalib::stringref attrName,
                            const IQueryEnvironment & env, IObjectStore & objectStore);
    /**
     * Used to lookup attribute from the most efficient source.
     **/
    static const IAttributeVector *
    lookupAttribute(const vespalib::string & key, vespalib::stringref attrName, const IQueryEnvironment & env);
    static vespalib::string createAttributeKey(vespalib::stringref attrName);

public:
    Blueprint(const Blueprint &) = delete;
    Blueprint &operator=(const Blueprint &) = delete;

    /**
     * Obtain the base name of this blueprint. This method will
     * typically only be invoked on the prototype object. The given
     * name is the base name of all feature executors that will be
     * indirectly created with this blueprint.
     *
     * An example scenario: A blueprint prototype is added with the
     * base name 'foo'. If the framework needs to calculate the feature
     * 'foo(a,b).out' it will first use the 'foo' prototype to create
     * a new instance of the appropriate class. The name of the newly
     * created blueprint will be set to 'foo(a,b)' and the setup
     * method will be invoked with 'a' and 'b' as parameters. After
     * inspecting the output names to find out which output has the
     * name 'out', the blueprint can be used to create a feature
     * executor that can perform the actual calculation of the
     * feature.
     *
     * @return blueprint base name
     **/
    const vespalib::string & getBaseName() const { return _baseName; }

    /**
     * This method may indicate which features that should be dumped
     * during a full feature dump by naming them to the given
     * visitor. The index environment is also given, since it may
     * affect the choice of which features to dump. Note that any
     * feature names can be given, but politeness indicate that only
     * those calculated by feature executors created through this
     * class should be given. Also note that naming non-existing
     * features here will break feature dumping.
     *
     * @param indexEnv the index environment
     * @param visitor the object visiting dump features
     **/
    virtual void visitDumpFeatures(const IIndexEnvironment &indexEnv,
                                   IDumpFeatureVisitor &visitor) const = 0;

    /**
     * Create another instance of this class. This must be implemented
     * by all the leafs in the inheritance hierarchy. (ref prototype
     * pattern)
     *
     * @return a new instance of this class (wrapped in an auto pointer)
     **/
    virtual UP createInstance() const = 0;

    /**
     * Set the name of this blueprint. This is the full name including
     * parameters. If the base name of a feature executor is 'foo' and
     * we are going to set up a blueprint for this executor with the
     * parameters 'a' and 'b', the name of this blueprint will be
     * 'foo(a,b)'. This method will be invoked by the framework right
     * before invoking the @ref setup method (and must not be invoked
     * by others).
     **/
    void setName(vespalib::stringref name) { _name = name; }

    /**
     * Obtain the name of this blueprint.
     *
     * @return blueprint name
     **/
    const string &getName() const { return _name; }

    /**
     * Returns the parameter descriptions for this blueprint.
     * The default implementation will return a description accepting all parameter lists.
     *
     * @return the parameter descriptions.
     **/
    virtual ParameterDescriptions getDescriptions() const;

    void attach_dependency_handler(DependencyHandler &dependency_handler) {
        _dependency_handler = &dependency_handler;
    }

    void detach_dependency_handler() {
        _dependency_handler = nullptr;
    }

    /**
     * Tailor this blueprint for the given set of parameters. The
     * implementation of this method should use the @ref defineInput
     * and @ref describeOutput methods.
     *
     * The default implementation of this function will validate
     * the parameters based on the parameter descriptions for this
     * blueprint, convert them to a parameter list, and call the
     * other setup function.
     *
     * @return false if the parameters does not make sense for this
     *               blueprint (aka setup failed)
     * @param indexEnv the index environment
     * @param params the parameters as simple strings
     **/
    virtual bool setup(const IIndexEnvironment &indexEnv,
                       const StringVector &params);

    /**
     * Setups this blueprint for the given set of parameters. The
     * implementation of this method should use the @ref defineInput
     * and @ref describeOutput methods.
     *
     * @return false if the parameters does not make sense for this
     *               blueprint (aka setup failed)
     * @param indexEnv the index environment.
     * @param params the parameters as a list of actual parameters.
     **/
    virtual bool setup(const IIndexEnvironment &indexEnv,
                       const ParameterList &params);

    /**
     * Here you can do some preprocessing. State must be stored in the IObjectStore.
     * This is called before creating multiple execution threads.
     * @param queryEnv The query environment.
     */
    virtual void prepareSharedState(const IQueryEnvironment & queryEnv, IObjectStore & objectStore) const;

    /**
     * Create a feature executor based on this blueprint. Failure to
     * initialize a feature executor for this blueprint may be
     * signaled by returning a shared pointer to 0.
     *
     * @return feature executor allocated in stash.
     * @param queryEnv query environment
     * @param stash    heterogenous object store
     **/
    virtual FeatureExecutor &createExecutor(const IQueryEnvironment &queryEnv,
                                            vespalib::Stash &stash) const = 0;

    /**
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~Blueprint();
};

}
