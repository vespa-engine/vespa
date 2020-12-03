// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.user;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeStream;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.api.integration.user.Roles;
import com.yahoo.vespa.hosted.controller.api.integration.user.User;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.RoleDefinition;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyResponse;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * API for user management related to access control.
 *
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class UserApiHandler extends LoggingRequestHandler {

    private final static Logger log = Logger.getLogger(UserApiHandler.class.getName());
    private static final String optionalPrefix = "/api";

    private final UserManagement users;
    private final Controller controller;
    private final BooleanFlag enable_public_signup_flow;
    private final IntFlag maxTrialTenants;

    @Inject
    public UserApiHandler(Context parentCtx, UserManagement users, Controller controller, FlagSource flagSource) {
        super(parentCtx);
        this.users = users;
        this.controller = controller;
        this.enable_public_signup_flow = PermanentFlags.ENABLE_PUBLIC_SIGNUP_FLOW.bindTo(flagSource);
        this.maxTrialTenants = Flags.MAX_TRIAL_TENANTS.bindTo(flagSource);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            Path path = new Path(request.getUri(), optionalPrefix);
            switch (request.getMethod()) {
                case GET: return handleGET(path, request);
                case POST: return handlePOST(path, request);
                case DELETE: return handleDELETE(path, request);
                case OPTIONS: return handleOPTIONS();
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(Path path, HttpRequest request) {
        if (path.matches("/user/v1/user")) return userMetadata(request);
        if (path.matches("/user/v1/tenant/{tenant}")) return listTenantRoleMembers(path.get("tenant"));
        if (path.matches("/user/v1/tenant/{tenant}/application/{application}")) return listApplicationRoleMembers(path.get("tenant"), path.get("application"));

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handlePOST(Path path, HttpRequest request) {
        if (path.matches("/user/v1/tenant/{tenant}")) return addTenantRoleMember(path.get("tenant"), request);
        if (path.matches("/user/v1/tenant/{tenant}/application/{application}")) return addApplicationRoleMember(path.get("tenant"), path.get("application"), request);

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handleDELETE(Path path, HttpRequest request) {
        if (path.matches("/user/v1/tenant/{tenant}")) return removeTenantRoleMember(path.get("tenant"), request);
        if (path.matches("/user/v1/tenant/{tenant}/application/{application}")) return removeApplicationRoleMember(path.get("tenant"), path.get("application"), request);

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handleOPTIONS() {
        EmptyResponse response = new EmptyResponse();
        response.headers().put("Allow", "GET,PUT,POST,PATCH,DELETE,OPTIONS");
        return response;
    }

    private static final Set<RoleDefinition> hostedOperators = Set.of(
            RoleDefinition.hostedOperator,
            RoleDefinition.hostedSupporter,
            RoleDefinition.hostedAccountant);

    private HttpResponse userMetadata(HttpRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, String> userAttributes = (Map<String, String>) getAttribute(request, User.ATTRIBUTE_NAME, Map.class);
        User user = new User(userAttributes.get("email"),
                             userAttributes.get("name"),
                             userAttributes.get("nickname"),
                             userAttributes.get("picture"));
        Set<Role> roles = getAttribute(request, SecurityContext.ATTRIBUTE_NAME, SecurityContext.class).roles();

        Map<TenantName, List<TenantRole>> tenantRolesByTenantName = roles.stream()
                .flatMap(role -> filterTenantRoles(role).stream())
                .distinct()
                .sorted(Comparator.comparing(Role::definition).reversed())
                .collect(Collectors.groupingBy(TenantRole::tenant, Collectors.toList()));

        // List of operator roles as defined in `hostedOperators` above
        List<Role> operatorRoles = roles.stream()
                .filter(role -> hostedOperators.contains(role.definition()))
                .sorted(Comparator.comparing(Role::definition))
                .collect(Collectors.toList());

        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setBool("isPublic", controller.system().isPublic());
        root.setBool("isCd", controller.system().isCd());
        root.setBool(enable_public_signup_flow.id().toString(),
                enable_public_signup_flow.with(FetchVector.Dimension.CONSOLE_USER_EMAIL, user.email()).value());
        root.setBool("hasTrialCapacity", hasTrialCapacity());

        toSlime(root.setObject("user"), user);

        Cursor tenants = root.setObject("tenants");
        InstanceName userInstance = InstanceName.from(user.nickname());
        tenantRolesByTenantName.keySet().stream()
                .sorted()
                .forEach(tenant -> {
                    Cursor tenantObject = tenants.setObject(tenant.value());

                    Cursor tenantRolesObject = tenantObject.setArray("roles");
                    tenantRolesByTenantName.getOrDefault(tenant, List.of())
                            .forEach(role -> tenantRolesObject.addString(role.definition().name()));
                });

        if (!operatorRoles.isEmpty()) {
            Cursor operator = root.setArray("operator");
            operatorRoles.forEach(role -> operator.addString(role.definition().name()));
        }

        return new SlimeJsonResponse(slime);
    }

    private HttpResponse listTenantRoleMembers(String tenantName) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("tenant", tenantName);
        fillRoles(root,
                  Roles.tenantRoles(TenantName.from(tenantName)),
                  Collections.emptyList());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse listApplicationRoleMembers(String tenantName, String applicationName) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("tenant", tenantName);
        root.setString("application", applicationName);
        fillRoles(root,
                  Roles.applicationRoles(TenantName.from(tenantName), ApplicationName.from(applicationName)),
                  Roles.tenantRoles(TenantName.from(tenantName)));
        return new SlimeJsonResponse(slime);
    }

    private void fillRoles(Cursor root, List<? extends Role> roles, List<? extends Role> superRoles) {
        Cursor rolesArray = root.setArray("roleNames");
        for (Role role : roles)
            rolesArray.addString(valueOf(role));

        Map<User, List<Role>> memberships = new LinkedHashMap<>();
        List<Role> allRoles = new ArrayList<>(superRoles); // Membership in a super role may imply membership in a role.
        allRoles.addAll(roles);
        for (Role role : allRoles)
            for (User user : users.listUsers(role)) {
                memberships.putIfAbsent(user, new ArrayList<>());
                memberships.get(user).add(role);
            }

        Cursor usersArray = root.setArray("users");
        memberships.forEach((user, userRoles) -> {
            Cursor userObject = usersArray.addObject();
            toSlime(userObject, user);

            Cursor rolesObject = userObject.setObject("roles");
            for (Role role : roles) {
                Cursor roleObject = rolesObject.setObject(valueOf(role));
                roleObject.setBool("explicit", userRoles.contains(role));
                roleObject.setBool("implied", userRoles.stream().anyMatch(userRole -> userRole.implies(role)));
            }
        });
    }

    private void toSlime(Cursor userObject, User user) {
        if (user.name() != null) userObject.setString("name", user.name());
        userObject.setString("email", user.email());
        if (user.nickname() != null) userObject.setString("nickname", user.nickname());
        if (user.picture() != null) userObject.setString("picture", user.picture());
    }

    private HttpResponse addTenantRoleMember(String tenantName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        if (requestObject.field("roles").valid()) {
            return addMultipleTenantRoleMembers(tenantName, requestObject);
        }
        return addTenantRoleMember(tenantName, requestObject);
    }

    private HttpResponse addTenantRoleMember(String tenantName, Inspector requestObject) {
        String roleName = require("roleName", Inspector::asString, requestObject);
        UserId user = new UserId(require("user", Inspector::asString, requestObject));
        Role role = Roles.toRole(TenantName.from(tenantName), roleName);
        users.addUsers(role, List.of(user));
        return new MessageResponse(user + " is now a member of " + role);
    }

    private HttpResponse addMultipleTenantRoleMembers(String tenantName, Inspector requestObject) {
        var tenant = TenantName.from(tenantName);
        var user = new UserId(require("user", Inspector::asString, requestObject));
        var roles = SlimeStream.fromArray(requestObject.field("roles"), Inspector::asString)
                .map(roleName -> Roles.toRole(tenant, roleName))
                .collect(Collectors.toUnmodifiableList());

        users.addToRoles(user, roles);
        return new MessageResponse(user + " is now a member of " + roles.stream().map(Role::toString).collect(Collectors.joining(", ")));
    }

    private HttpResponse addApplicationRoleMember(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        UserId user = new UserId(require("user", Inspector::asString, requestObject));
        Role role = Roles.toRole(TenantName.from(tenantName), ApplicationName.from(applicationName), roleName);
        users.addUsers(role, List.of(user));
        return new MessageResponse(user + " is now a member of " + role);
    }

    private HttpResponse removeTenantRoleMember(String tenantName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        if (requestObject.field("roles").valid()) {
            return removeMultipleTenantRoleMembers(tenantName, requestObject);
        }
        return removeTenantRoleMember(tenantName, requestObject);
    }

    private HttpResponse removeTenantRoleMember(String tenantName, Inspector requestObject) {
        TenantName tenant = TenantName.from(tenantName);
        String roleName = require("roleName", Inspector::asString, requestObject);
        UserId user = new UserId(require("user", Inspector::asString, requestObject));
        List<Role> roles = Collections.singletonList(Roles.toRole(tenant, roleName));

        enforceLastAdminOfTenant(tenant, user, roles);
        removeDeveloperKey(tenant, user, roles);
        users.removeFromRoles(user, roles);

        return new MessageResponse(user + " is no longer a member of " + roles.stream().map(Role::toString).collect(Collectors.joining(", ")));
    }

    private HttpResponse removeMultipleTenantRoleMembers(String tenantName, Inspector requestObject) {
        var tenant = TenantName.from(tenantName);
        var user = new UserId(require("user", Inspector::asString, requestObject));
        var roles = SlimeStream.fromArray(requestObject.field("roles"), Inspector::asString)
                .map(roleName -> Roles.toRole(tenant, roleName))
                .collect(Collectors.toUnmodifiableList());

        enforceLastAdminOfTenant(tenant, user, roles);
        removeDeveloperKey(tenant, user, roles);
        users.removeFromRoles(user, roles);

        return new MessageResponse(user + " is no longer a member of " + roles.stream().map(Role::toString).collect(Collectors.joining(", ")));
    }

    private void enforceLastAdminOfTenant(TenantName tenantName, UserId user, List<Role> roles) {
        for (Role role : roles) {
            if (role.definition().equals(RoleDefinition.administrator)) {
                if (Set.of(user.value()).equals(users.listUsers(role).stream().map(User::email).collect(Collectors.toSet()))) {
                    throw new IllegalArgumentException("Can't remove the last administrator of a tenant.");
                }
                break;
            }
        }
    }

    private void removeDeveloperKey(TenantName tenantName, UserId user, List<Role> roles) {
        for (Role role : roles) {
            if (role.definition().equals(RoleDefinition.developer)) {
                controller.tenants().lockIfPresent(tenantName, LockedTenant.Cloud.class, tenant -> {
                    PublicKey key = tenant.get().developerKeys().inverse().get(new SimplePrincipal(user.value()));
                    if (key != null)
                        controller.tenants().store(tenant.withoutDeveloperKey(key));
                });
                break;
            }
        }
    }

    private HttpResponse removeApplicationRoleMember(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        UserId user = new UserId(require("user", Inspector::asString, requestObject));
        Role role = Roles.toRole(TenantName.from(tenantName), ApplicationName.from(applicationName), roleName);
        users.removeUsers(role, List.of(user));
        return new MessageResponse(user + " is no longer a member of " + role);
    }

    private boolean hasTrialCapacity() {
        if (! controller.system().isPublic()) return true;
        var existing = controller.tenants().asList().stream().map(Tenant::name).collect(Collectors.toList());
        var trialTenants = controller.serviceRegistry().billingController().tenantsWithPlan(existing, PlanId.from("trial"));
        return maxTrialTenants.value() < 0 || trialTenants.size() < maxTrialTenants.value();
    }

    private static Inspector bodyInspector(HttpRequest request) {
        return Exceptions.uncheck(() -> SlimeUtils.jsonToSlime(IOUtils.readBytes(request.getData(), 1 << 10)).get());
    }

    private <Type> Type require(String name, Function<Inspector, Type> mapper, Inspector object) {
        if ( ! object.field(name).valid()) throw new IllegalArgumentException("Missing field '" + name + "'.");
        return mapper.apply(object.field(name));
    }

    private static String valueOf(Role role) {
        switch (role.definition()) {
            case administrator:         return "administrator";
            case developer:             return "developer";
            case reader:                return "reader";
            case headless:              return "headless";
            default: throw new IllegalArgumentException("Unexpected role type '" + role.definition() + "'.");
        }
    }

    private static Collection<TenantRole> filterTenantRoles(Role role) {
        if (!(role instanceof TenantRole))
            return Set.of();

        TenantRole tenantRole = (TenantRole) role;
        if (tenantRole.definition() == RoleDefinition.administrator
                || tenantRole.definition() == RoleDefinition.developer
                || tenantRole.definition() == RoleDefinition.reader)
            return Set.of(tenantRole);

        if (tenantRole.definition() == RoleDefinition.athenzTenantAdmin)
            return Roles.tenantRoles(tenantRole.tenant());

        return Set.of();
    }

    private static <T> T getAttribute(HttpRequest request, String attributeName, Class<T> clazz) {
        return Optional.ofNullable(request.getJDiscRequest().context().get(attributeName))
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .orElseThrow(() -> new IllegalArgumentException("Attribute '" + attributeName + "' was not set on request"));
    }
}
