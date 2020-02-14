// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.telegraf;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.system.execution.ProcessExecutor;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.FileWriter;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author olaa
 */
public class Telegraf extends AbstractComponent {

    private static final String TELEGRAF_CONFIG_PATH = "/etc/telegraf/telegraf.conf";
    private static final String TELEGRAF_CONFIG_TEMPLATE_PATH = "src/main/resources/templates/cloudwatch_plugin.vm";
    private final TelegrafRegistry telegrafRegistry;

    @Inject
    public Telegraf(TelegrafRegistry telegrafRegistry, TelegrafConfig telegrafConfig) {
        this.telegrafRegistry = telegrafRegistry;
        telegrafRegistry.addInstance(this);
        writeConfig(telegrafConfig);
        restartTelegraf();
    }

    private void writeConfig(TelegrafConfig telegrafConfig) {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();
        Template template = velocityEngine.getTemplate(TELEGRAF_CONFIG_TEMPLATE_PATH);

        VelocityContext context = new VelocityContext();
        context.put("intervalSeconds", telegrafConfig.intervalSeconds());
        context.put("cloudwatchRegion", telegrafConfig.cloudWatch().region());
        context.put("cloudwatchNamespace", telegrafConfig.cloudWatch().namespace());
        context.put("cloudwatchSecretKey", telegrafConfig.cloudWatch().secretKeyName());
        context.put("cloudwatchAccessKey", telegrafConfig.cloudWatch().accessKeyName());
        context.put("cloudwatchProfile", telegrafConfig.cloudWatch().profile());
        context.put("isHosted", !telegrafConfig.cloudWatch().secretKeyName().isBlank());
        context.put("vespaConsumer", telegrafConfig.vespa().consumer());
        // TODO: Add node cert if hosted

        FileWriter writer = uncheck(() -> new FileWriter(TELEGRAF_CONFIG_PATH));
        template.merge(context, writer);
        uncheck(writer::close);
    }

    private void restartTelegraf() {
        executeCommand("service telegraf restart");
    }

    private void stopTelegraf() {
        executeCommand("service telegraf stop");
    }

    private void executeCommand(String command) {
        ProcessExecutor processExecutor = new ProcessExecutor
                .Builder(10)
                .successExitCodes(0)
                .build();
        uncheck(() -> processExecutor.execute(command))
                .orElseThrow(() -> new RuntimeException("Running " + command + " timed out"));
    }

    @Override
    public void deconstruct() {
        telegrafRegistry.removeInstance(this);
        if (telegrafRegistry.isEmpty()) {
            stopTelegraf();
        }
    }
}
