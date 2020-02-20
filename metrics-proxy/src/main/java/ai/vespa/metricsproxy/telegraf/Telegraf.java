// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.telegraf;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.system.execution.ProcessExecutor;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author olaa
 */
public class Telegraf extends AbstractComponent {

    private static final String TELEGRAF_CONFIG_PATH = "/etc/telegraf/telegraf.conf";
    private static final String TELEGRAF_CONFIG_TEMPLATE_PATH = "templates/telegraf.conf.vm";
    private final TelegrafRegistry telegrafRegistry;

    private static final Logger logger = Logger.getLogger(Telegraf.class.getName());

    @Inject
    public Telegraf(TelegrafRegistry telegrafRegistry, TelegrafConfig telegrafConfig) {
        this.telegrafRegistry = telegrafRegistry;
        telegrafRegistry.addInstance(this);
        writeConfig(telegrafConfig, uncheck(() -> new FileWriter(TELEGRAF_CONFIG_PATH)));
        restartTelegraf();
    }

    protected static void writeConfig(TelegrafConfig telegrafConfig, Writer writer) {
        VelocityContext context = new VelocityContext();
        context.put("intervalSeconds", telegrafConfig.intervalSeconds());
        context.put("cloudwatchPlugins", telegrafConfig.cloudWatch());
        // TODO: Add node cert if hosted

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();
        velocityEngine.evaluate(context, writer, "TelegrafConfigWriter", getTemplateReader());
        uncheck(writer::close);
    }

    private void restartTelegraf() {
        logger.info("Restarting Telegraf");
        executeCommand("service telegraf restart");
    }

    private void stopTelegraf() {
        logger.info("Stopping Telegraf");
        executeCommand("service telegraf stop");
    }

    private void executeCommand(String command) {
        ProcessExecutor processExecutor = new ProcessExecutor
                .Builder(10)
                .successExitCodes(0)
                .build();
        uncheck(() -> processExecutor.execute(command))
                .orElseThrow(() -> new RuntimeException("Timed out running command: " + command));
    }

    @SuppressWarnings("ConstantConditions")
    private static Reader getTemplateReader() {
        return new InputStreamReader(Telegraf.class.getClassLoader()
                                        .getResourceAsStream(TELEGRAF_CONFIG_TEMPLATE_PATH)
        );

    }

    @Override
    public void deconstruct() {
        telegrafRegistry.removeInstance(this);
        if (telegrafRegistry.isEmpty()) {
            stopTelegraf();
        }
    }
}
