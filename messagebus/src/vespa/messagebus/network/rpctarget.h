// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/common.h>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/component/version.h>

namespace mbus {

/**
 * Implements a target object that encapsulates the FRT connection
 * target. Instances of this class are returned by {@link RPCService}, and
 * cached by {@link RPCTargetPool}.
 */
class RPCTarget : public FRT_IRequestWait, public std::enable_shared_from_this<RPCTarget> {
public:
    /**
     * Declares a version handler used when resolving the version of a target.
     * An instance of this is passed to {@link RPCTarget#resolveVersion(double,
     * VersionHandler)}, and invoked either synchronously or asynchronously,
     * depending on whether or not the version is already available.
     */
    class IVersionHandler {
    public:
        /**
         * Virtual destructor required for inheritance.
         */
        virtual ~IVersionHandler() = default;

        /**
         * This method is invoked once the version of the corresponding {@link
         * RPCTarget} becomes available. If a problem occurred while retrieving
         * the version, this method is invoked with a null argument.
         *
         * @param ver The version of corresponding target, or null.
         */
        virtual void handleVersion(const vespalib::Version *ver) = 0;
    };

private:
    using HandlerList = std::vector<IVersionHandler*>;

    enum ResolveState {
        VERSION_NOT_RESOLVED,
        TARGET_INVOKED,
        PROCESSING_HANDLERS,
        VERSION_RESOLVED,
    };
    using Version_UP = std::unique_ptr<vespalib::Version>;

    std::mutex                 _lock;
    std::condition_variable    _cond;
    FRT_Supervisor            &_orb;
    string                     _name;
    FRT_Target                &_target;
    std::atomic<ResolveState>  _state;
    Version_UP                 _version;
    HandlerList                _versionHandlers;

    struct ctor_tag {};
public:
    /**
     * Convenience typedefs.
     */
    using SP = std::shared_ptr<RPCTarget>;

    /**
     * Constructs a new instance of this class. This object creates and
     * takes ownership of a corresponding FRT target, and will deref it
     * upon destruction.
     *
     * @param spec The connection spec of this target.
     * @param orb  The FRT supervisor to use when connecting to target.
     */
    RPCTarget(const string &name, FRT_Supervisor &orb, ctor_tag);
    static SP create(const string &name, FRT_Supervisor &orb) {
        return std::make_shared<RPCTarget>(name, orb, ctor_tag{});
    }

    /**
     * Destructor. Subrefs the contained FRT target.
     */
    ~RPCTarget();

    /**
     * Requests the version of this target be passed to the given {@link
     * VersionHandler}. If the version is available, the handler is called
     * synchronously; if not, the handler is called by the network thread once
     * the target responds to the version query.
     *
     * @param timeout The timeout for the request in milliseconds.
     * @param handler The handler to be called once the version is available.
     */
    void resolveVersion(duration timeout, IVersionHandler &handler);

    /**
     * @return true if the FRT target is valid or has been invoked (which
     *   means we cannot destroy it).
     */
    bool isValid() const;

    /**
     * Returns the encapsulated FRT target.
     *
     * @return The target.
     */
    FRT_Target &getFRTTarget() { return _target; }

    /**
     * Returns the version to use when communicating with this target.
     * Version must have been successfully resolved before calling this
     * function.
     *
     * @return The negotiated version.
     */
    const vespalib::Version &getVersion() const { return *_version; }

    // Implements FRT_IRequestWait.
    void RequestDone(FRT_RPCRequest *req) override;
};

} // namespace mbus

