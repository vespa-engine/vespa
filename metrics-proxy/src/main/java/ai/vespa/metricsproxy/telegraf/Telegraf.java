// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.telegraf;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;

import java.io.File;
import java.util.logging.Level;
import com.yahoo.system.execution.ProcessExecutor;
import com.yahoo.system.execution.ProcessResult;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.logging.Logger;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author olaa
 */
public class Telegraf extends AbstractComponent {

    // These paths must coincide with the paths in the start/stop-telegraf shell scripts.
    private static final String TELEGRAF_CONFIG_PATH = getDefaults().underVespaHome("conf/telegraf/telegraf.conf");
    private static final String TELEGRAF_LOG_FILE_PATH = getDefaults().underVespaHome("logs/telegraf/telegraf.log");

    private static final String START_TELEGRAF_SCRIPT = getDefaults().underVespaHome("libexec/vespa/start-telegraf.sh");
    private static final String STOP_TELEGRAF_SCRIPT = getDefaults().underVespaHome("libexec/vespa/stop-telegraf.sh");

    private static final String TELEGRAF_CONFIG_TEMPLATE_PATH = "templates/telegraf.conf.vm";

    private final TelegrafRegistry telegrafRegistry;

    private static final Logger logger = Logger.getLogger(Telegraf.class.getName());

    @Inject
    public Telegraf(TelegrafRegistry telegrafRegistry, TelegrafConfig telegrafConfig) {
        this.telegrafRegistry = telegrafRegistry;
        telegrafRegistry.addInstance(this);
        writeConfig(telegrafConfig, getConfigWriter(), TELEGRAF_LOG_FILE_PATH);
        restartTelegraf();
    }

    protected static void writeConfig(TelegrafConfig telegrafConfig, Writer writer, String logFilePath) {
        VelocityContext context = new VelocityContext();
        context.put("logFilePath", logFilePath);
        context.put("intervalSeconds", telegrafConfig.intervalSeconds());
        context.put("cloudwatchPlugins", telegrafConfig.cloudWatch());
        context.put("protocol", telegrafConfig.isHostedVespa() ? "https" : "http");
        // TODO: Add node cert if hosted

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();
        velocityEngine.evaluate(context, writer, "TelegrafConfigWriter", getTemplateReader());
        uncheck(writer::close);
    }

    private void restartTelegraf() {
        executeCommand(STOP_TELEGRAF_SCRIPT);
        executeCommand(START_TELEGRAF_SCRIPT);
    }

    private void stopTelegraf() {
        executeCommand(STOP_TELEGRAF_SCRIPT);
    }

    private void executeCommand(String command) {
        logger.info(String.format("Running command: %s", command));
        ProcessExecutor processExecutor = new ProcessExecutor
                .Builder(10)
                .successExitCodes(0)
                .build();
        ProcessResult processResult = uncheck(() -> processExecutor.execute(command))
                .orElseThrow(() -> new RuntimeException("Timed out running command: " + command));

        logger.log(Level.FINE, () -> String.format("Exit code: %d\nstdOut: %s\nstdErr: %s",
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

    private static Writer getConfigWriter() {
        File configFile = new File(TELEGRAF_CONFIG_PATH);
        configFile.getParentFile().mkdirs();
        return uncheck(() -> new FileWriter(configFile));
    }

    @Override
    public void deconstruct() {
        telegrafRegistry.removeInstance(this);
        if (telegrafRegistry.isEmpty()) {
            stopTelegraf();
        }
    }
}
