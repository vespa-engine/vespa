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
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.user.RoleId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.ApplicationRole;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.Roles;
import com.yahoo.vespa.hosted.controller.api.role.TenantRole;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.MessageResponse;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.vespa.hosted.controller.restapi.application.EmptyJsonResponse;
import com.yahoo.yolean.Exceptions;

import java.util.List;
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

    private final Roles roles;
    private final UserManagement users;
    private final Controller controller;

    @Inject
    public UserApiHandler(Context parentCtx, Roles roles, UserManagement users, Controller controller) {
        super(parentCtx);
        this.roles = roles;
        this.users = users;
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET:
                    return handleGET(request);
                case POST:
                    return handlePOST(request);
                case DELETE:
                    return handleDELETE(request);
                case OPTIONS:
                    return handleOPTIONS();
                default:
                    return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
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
        Cursor rolesArray = root.setArray("roles");
        for (TenantRole role : tenantRoles(TenantName.from(tenantName))) {
            Cursor roleObject = rolesArray.addObject();
            roleObject.setString("name", role.definition().name());
            Cursor membersArray = roleObject.setArray("members");
            for (UserId user : users.listUsers(RoleId.fromRole(role)))
                membersArray.addString(user.value());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse listApplicationRoleMembers(String tenantName, String applicationName) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("tenant", tenantName);
        root.setString("application", applicationName);
        Cursor rolesArray = root.setArray("roles");
        for (ApplicationRole role : applicationRoles(TenantName.from(tenantName), ApplicationName.from(applicationName))) {
            Cursor roleObject = rolesArray.addObject();
            roleObject.setString("name", role.definition().name());
            Cursor membersArray = roleObject.setArray("members");
            for (UserId user : users.listUsers(RoleId.fromRole(role)))
                membersArray.addString(user.value());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse addTenantRoleMember(String tenantName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        String user = require("user", Inspector::asString, requestObject);
        RoleId roleId = RoleId.fromValue(tenantName + "." + roleName); // TODO jvenstad: Move this logic to utility class CloudRoles, with validation.
        users.addUsers(roleId, List.of(new UserId(user)));
        return new MessageResponse("User '" + user + "' is now a member of role '" + roleId + "'.");
    }

    private HttpResponse addApplicationRoleMember(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        String user = require("user", Inspector::asString, requestObject);
        RoleId roleId = RoleId.fromValue(tenantName + "." + applicationName + "." + roleName); // TODO jvenstad: Move this logic to utility class CloudRoles, with validation.
        users.addUsers(roleId, List.of(new UserId(user)));
        return new MessageResponse("User '" + user + "' is now a member of role '" + roleId + "'.");
    }

    private HttpResponse removeTenantRoleMember(String tenantName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        String user = require("user", Inspector::asString, requestObject);
        RoleId roleId = RoleId.fromValue(tenantName + "." + roleName); // TODO jvenstad: Move this logic to utility class CloudRoles, with validation.
        users.removeUsers(roleId, List.of(new UserId(user)));
        return new MessageResponse("User '" + user + "' is no longer a member of role '" + roleId + "'.");
    }

    private HttpResponse removeApplicationRoleMember(String tenantName, String applicationName, HttpRequest request) {
        Inspector requestObject = bodyInspector(request);
        String roleName = require("roleName", Inspector::asString, requestObject);
        String user = require("user", Inspector::asString, requestObject);
        RoleId roleId = RoleId.fromValue(tenantName + "." + applicationName + "." + roleName); // TODO jvenstad: Move this logic to utility class CloudRoles, with validation.
        users.removeUsers(roleId, List.of(new UserId(user)));
        return new MessageResponse("User '" + user + "' is no longer a member of role '" + roleId + "'.");
    }

    private List<TenantRole> tenantRoles(TenantName tenant) { // TODO jvenstad: Move these two to CloudRoles utility class.
        return List.of(roles.tenantOperator(tenant),
                       roles.tenantAdmin(tenant),
                       roles.tenantOwner(tenant));
    }

    private List<ApplicationRole> applicationRoles(TenantName tenant, ApplicationName application) {
        return List.of(roles.applicationReader(tenant, application),
                       roles.applicationDeveloper(tenant, application),
                       roles.applicationOperator(tenant, application),
                       roles.applicationAdmin(tenant, application));
    }

    private static Inspector bodyInspector(HttpRequest request) {
        return Exceptions.uncheck(() -> SlimeUtils.jsonToSlime(IOUtils.readBytes(request.getData(), 1 << 10)).get());
    }

    private <Type> Type require(String name, Function<Inspector, Type> mapper, Inspector object) {
        if ( ! object.field(name).valid()) throw new IllegalArgumentException("Missing field '" + name + "'.");
        return mapper.apply(object.field(name));
    }

}
