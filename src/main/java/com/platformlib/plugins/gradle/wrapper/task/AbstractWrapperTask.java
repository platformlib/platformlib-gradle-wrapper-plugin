package com.platformlib.plugins.gradle.wrapper.task;

import com.platformlib.plugins.gradle.wrapper.extension.PlatformLibGradleWrapperExtension;
import com.platformlib.plugins.gradle.wrapper.configuration.PlatformLibGradleWrapperConfiguration;
import com.platformlib.plugins.gradle.wrapper.utility.PlatformLibGradleWrapperUtility;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractWrapperTask<T extends PlatformLibGradleWrapperConfiguration> extends DefaultTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWrapperTask.class);
    private T wrapperConfiguration;
    private String id;

    @TaskAction
    public void execute() {
        if (wrapperConfiguration.isDryRun()) {
            LOGGER.info("Dry run mode");
        }
        final PlatformLibGradleWrapperExtension platformLibGradleWrapperExtension = getProject().getRootProject().getExtensions().getByType(PlatformLibGradleWrapperExtension.class);
        final List<String> taskNames = new ArrayList<>(getProject().getGradle().getStartParameter().getTaskNames());
        taskNames.removeAll(PlatformLibGradleWrapperUtility.toStringCollection(platformLibGradleWrapperExtension.getUnwrappedTasks()));
        final Collection<String> cla = new ArrayList<>();

        if (!platformLibGradleWrapperExtension.isParallelExecution()) {
            cla.add("-s");
        }

        if (!platformLibGradleWrapperExtension.isParallelExecution()) {
            cla.add("-P" + PlatformLibGradleWrapperExtension.GRADLE_WRAPPER_PLUGIN_PROJECT_PROPERTY + "=false");
        }

        final Map<String, String> projectParameters = new HashMap<>(getProject().getGradle().getStartParameter().getProjectProperties());
        projectParameters.putAll(wrapperConfiguration.getProjectProperties());
        wrapperConfiguration.getExcludedProjectProperties().forEach(projectParameters::remove);
        projectParameters.forEach((k, v) -> cla.add("-P" + k + "=" + v));

        final Map<String, String> systemParameters = new HashMap<>(getProject().getGradle().getStartParameter().getSystemPropertiesArgs());
        systemParameters.putAll(wrapperConfiguration.getSystemPropertiesArgs());
        wrapperConfiguration.getExcludedSystemPropertiesArgs().forEach(systemParameters::remove);
        systemParameters.forEach((k, v) -> cla.add("-D" + k + "=" + v));

        if (wrapperConfiguration.isDebug()) {
            cla.add("--debug");
        } else {
            switch (getProject().getGradle().getStartParameter().getLogLevel()) {
                case INFO:
                case WARN:
                case DEBUG:
                case QUIET:
                    cla.add("-Dorg.gradle.logging.level=" + getProject().getGradle().getStartParameter().getLogLevel().name().toLowerCase(Locale.ROOT));
                    break;
                default:
                    //Nothing to do, lifecycle is default level
            }
        }

        cla.addAll(taskNames);
        getProject().getGradle().getStartParameter().getExcludedTaskNames().forEach(taskName -> cla.add("-x" + taskName));
        executeWrappedGradle(cla);
    }

    abstract protected void executeWrappedGradle(Collection<String> gradleCommandAndArguments);

    @Input
    public T getConfiguration() {
        return wrapperConfiguration;
    }

    public void setConfiguration(final T wrapperConfiguration) {
        this.wrapperConfiguration = wrapperConfiguration;
    }

    @Input
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
