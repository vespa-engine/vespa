// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.telegraf;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.log.LogLevel;
import com.yahoo.system.execution.ProcessExecutor;
import com.yahoo.system.execution.ProcessResult;
import com.yahoo.vespa.defaults.Defaults;
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
    private static final String TELEGRAF_LOG_FILE_PATH = Defaults.getDefaults().underVespaHome("logs/telegraf/telegraf.log");
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
        context.put("logFilePath", TELEGRAF_LOG_FILE_PATH);
        context.put("intervalSeconds", telegrafConfig.intervalSeconds());
        context.put("cloudwatchPlugins", telegrafConfig.cloudWatch());
        // TODO: Add node cert if hosted

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();
        velocityEngine.evaluate(context, writer, "TelegrafConfigWriter", getTemplateReader());
        uncheck(writer::close);
    }

    private void restartTelegraf() {
        executeCommand("service telegraf restart");
    }

    private void stopTelegraf() {
        executeCommand("service telegraf stop");
    }

    private void executeCommand(String command) {
        logger.info(String.format("Running command: %s", command));
        ProcessExecutor processExecutor = new ProcessExecutor
                .Builder(10)
                .successExitCodes(0)
                .build();
        ProcessResult processResult = uncheck(() -> processExecutor.execute(command))
                .orElseThrow(() -> new RuntimeException("Timed out running command: " + command));

        logger.log(LogLevel.DEBUG, () -> String.format("Exit code: %d\nstdOut: %s\nstdErr: %s",
                                                        processResult.exitCode,
                                                        processResult.stdOut,
                                                        processResult.stdErr));

        if (!processResult.stdErr.isBlank())
            logger.warning(String.format("stdErr not empty: %s", processResult.stdErr));
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
