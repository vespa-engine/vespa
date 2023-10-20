// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.notification;

import com.yahoo.config.provision.TenantName;
import com.yahoo.restapi.UriBuilder;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;
import com.yahoo.yolean.Exceptions;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.apache.velocity.tools.generic.EscapeTool;

import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * @author bjorncs
 */
public class MailTemplating {

    public enum Template {
        MAIL("mail"),  DEFAULT_MAIL_CONTENT("default-mail-content"), NOTIFICATION_MESSAGE("notification-message"),
        CLOUD_TRIAL_NOTIFICATION("cloud-trial-notification"), MAIL_VERIFICATION("mail-verification");

        public static Optional<Template> fromId(String id) {
            return Arrays.stream(values()).filter(t -> t.id.equals(id)).findAny();
        }

        private final String id;

        Template(String id) { this.id = id; }

        public String getId() { return id; }
    }

    private final VelocityEngine velocity;
    private final EscapeTool escapeTool = new EscapeTool();
    private final URI dashboardUri;

    public MailTemplating(ZoneRegistry zoneRegistry) {
        this.velocity = createTemplateEngine();
        this.dashboardUri = zoneRegistry.dashboardUrl();
    }

    public String generateDefaultMailHtml(Template mailBodyTemplate, Map<String, Object> params, TenantName tenant) {
        var ctx = createVelocityContext();
        ctx.put("accountNotificationLink", accountNotificationsUri(tenant));
        ctx.put("privacyPolicyLink", "https://legal.yahoo.com/xw/en/yahoo/privacy/topic/b2bprivacypolicy/index.html");
        ctx.put("termsOfServiceLink", consoleUri("terms-of-service-trial.html"));
        ctx.put("supportLink", consoleUri("support"));
        ctx.put("mailBodyTemplate", mailBodyTemplate.getId());
        params.forEach(ctx::put);
        return render(ctx, Template.MAIL);
    }

    public String generateMailVerificationHtml(PendingMailVerification pmf) {
        var ctx = createVelocityContext();
        ctx.put("consoleLink", dashboardUri.getHost());
        ctx.put("email", pmf.getMailAddress());
        ctx.put("code", pmf.getVerificationCode());
        return render(ctx, Template.MAIL_VERIFICATION);
    }

    public String escapeHtml(String s) { return escapeTool.html(s); }

    private VelocityContext createVelocityContext() {
        var ctx = new VelocityContext();
        ctx.put("esc", escapeTool);
        return ctx;
    }

    private String render(VelocityContext ctx, Template template) {
        var writer = new StringWriter();
        // Ignoring return value - implementation either returns 'true' or throws, never 'false'
        velocity.mergeTemplate(template.getId(), StandardCharsets.UTF_8.name(), ctx, writer);
        return writer.toString();
    }

    private static VelocityEngine createTemplateEngine() {
        var v = new VelocityEngine();
        v.setProperty(Velocity.RESOURCE_LOADERS, "string");
        v.setProperty(Velocity.RESOURCE_LOADER + ".string.class", StringResourceLoader.class.getName());
        v.setProperty(Velocity.RESOURCE_LOADER + ".string.repository.static", "false");
        v.init();
        var repo = (StringResourceRepository) v.getApplicationAttribute(StringResourceLoader.REPOSITORY_NAME_DEFAULT);
        Arrays.stream(Template.values()).forEach(t -> registerTemplate(repo, t.getId()));
        return v;
    }

    private static void registerTemplate(StringResourceRepository repo, String name) {
        var templateStr = Exceptions.uncheck(() -> {
            var in = MailTemplating.class.getResourceAsStream("/mail/%s.vm".formatted(name));
            return new String(in.readAllBytes());
        });
        repo.putStringResource(name, templateStr);
    }

    private String accountNotificationsUri(TenantName tenant) {
        return new UriBuilder(dashboardUri)
                .append("tenant/")
                .append(tenant.value())
                .append("account/notifications")
                .toString();
    }

    private String consoleUri(String path) {
        return new UriBuilder(dashboardUri).append(path).toString();
    }
}
