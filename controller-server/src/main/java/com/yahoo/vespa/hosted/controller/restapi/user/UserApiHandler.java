// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.user;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserRoles;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.Roles;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.MessageResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyJsonResponse;
import com.yahoo.yolean.Exceptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API for user management related to access control.
 *
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class UserApiHandler extends LoggingRequestHandler {

    private final static Logger log = Logger.getLogger(UserApiHandler.class.getName());

    private final UserRoles roles;
    private final UserManagement users;

    @Inject
    public UserApiHandler(Context parentCtx, Roles roles, UserManagement users) {
        super(parentCtx);
        this.roles = new UserRoles(roles);
        this.users = users;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                case POST: return handlePOST(request);
                case DELETE: return handleDELETE(request);
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

    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/user/v1/tenant/{tenant}")) return listTenantRoleMembers(path.get("tenant"));
        if (path.matches("/user/v1/tenant/{tenant}/application/{application}")) return listApplicationRoleMembers(path.get("tenant"), path.get("application"));

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handlePOST(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/user/v1/tenant/{tenant}")) return addTenantRoleMember(path.get("tenant"), request);
        if (path.matches("/user/v1/tenant/{tenant}/application/{application}")) return addApplicationRoleMember(path.get("tenant"), path.get("application"), request);

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handleDELETE(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/user/v1/tenant/{tenant}")) return removeTenantRoleMember(path.get("tenant"), request);
        if (path.matches("/user/v1/tenant/{tenant}/application/{application}")) return removeApplicationRoleMember(path.get("tenant"), path.get("application"), request);

        return ErrorResponse.notFoundError(String.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    private HttpResponse handleOPTIONS() {
        EmptyJsonResponse response = new EmptyJsonResponse();
        response.headers().put("Allow", "GET,PUT,POST,PATCH,DELETE,OPTIONS");
        return response;
    }

    private HttpResponse listTenantRoleMembers(String tenantName) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("tenant", tenantName);
        fillRoles(root, roles.tenantRoles(TenantName.from(tenantName)));
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse listApplicationRoleMembers(String tenantName, String applicationName) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("tenant", tenantName);
        root.setString("application", applicationName);
        fillRoles(root, roles.applicationRoles(TenantName.from(tenantName), ApplicationName.from(applicationName)));
        return new SlimeJsonResponse(slime);
    }

    private void fillRoles(Cursor root, List<? extends Role> roles) {
        Cursor rolesArray = root.setArray("roleNames");
        for (Role role : roles)
            rolesArray.addString(valueOf(role));

        Map<UserId, List<Role>> memberships = new HashMap<>();
        for (Role role : roles)
            for (UserId user : users.listUsers(role)) {
                memberships.putIfAbsent(user, new ArrayList<>());
                memberships.get(user).add(role);
            }

        Cursor usersArray = root.setArray("users");
        memberships.forEach((user, userRoles) -> {
            Cursor userObject = usersArray.addObject();
            userObject.setString("name", user.value());
            Cursor rolesObject = userObject.setObject("roles");
            for (Role role : roles) {
                Cursor roleObject = rolesObject.setObject(valueOf(role));
                roleObject.setBool("explicit", userRoles.contains(role));
                roleObject.setBool("implied", userRoles.stream().anyMatch(userRole -> userRole.implies(role)));
            }
        });
    }

    private HttpResponse addTenantRoleMember(String tenantName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        String user = require("user", Inspector::asString, requestObject);
        Role role = roles.toRole(TenantName.from(tenantName), roleName);
        users.addUsers(role, List.of(new UserId(user)));
        return new MessageResponse(user + " is now a member of " + role);
    }

    private HttpResponse addApplicationRoleMember(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        String user = require("user", Inspector::asString, requestObject);
        Role role = roles.toRole(TenantName.from(tenantName), ApplicationName.from(applicationName), roleName);
        users.addUsers(role, List.of(new UserId(user)));
        return new MessageResponse(user + " is now a member of " + role);
    }

    private HttpResponse removeTenantRoleMember(String tenantName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        String user = require("user", Inspector::asString, requestObject);
        Role role = roles.toRole(TenantName.from(tenantName), roleName);
        users.removeUsers(role, List.of(new UserId(user)));
        return new MessageResponse(user + " is no longer a member of " + role);
    }

    private HttpResponse removeApplicationRoleMember(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        String user = require("user", Inspector::asString, requestObject);
        Role role = roles.toRole(TenantName.from(tenantName), ApplicationName.from(applicationName), roleName);
        users.removeUsers(role, List.of(new UserId(user)));
        return new MessageResponse(user + " is no longer a member of " + role);
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
            case tenantOwner:           return "tenantOwner";
            case tenantAdmin:           return "tenantAdmin";
            case tenantOperator:        return "tenantOperator";
            case applicationAdmin:      return "applicationAdmin";
            case applicationOperator:   return "applicationOperator";
            case applicationDeveloper:  return "applicationDeveloper";
            case applicationReader:     return "applicationReader";
            default: throw new IllegalArgumentException("Unexpected role type '" + role.definition() + "'.");
        }
    }

}
