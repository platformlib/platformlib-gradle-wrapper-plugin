package com.platformlib.plugins.gradle.container.task;

import com.platformlib.os.api.OsPlatform;
import com.platformlib.os.api.enums.OsFamily;
import com.platformlib.os.api.factory.OsPlatforms;
import com.platformlib.os.local.LocalOsPlatform;
import com.platformlib.plugins.gradle.container.PlatformLibGradleContainerPlugin;
import com.platformlib.plugins.gradle.container.container.PlatformLibContainerExtension;
import com.platformlib.plugins.gradle.container.utility.PlatformLibContainerUtility;
import com.platformlib.plugins.gradle.wrapper.state.ContainerCommandState;
import com.platformlib.process.api.ProcessInstance;
import com.platformlib.process.builder.ProcessBuilder;
import com.platformlib.process.configurator.ProcessOutputConfigurator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Task to be executed in docker container.
 */
public abstract class ContainerTask extends DefaultTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerTask.class);

    @Input
    public abstract Property<Collection<Object>> getCommandAndArguments();

    @Input
    @Optional
    public abstract Property<String> getWorkDir();

    @Input
    @Optional
    public abstract Property<Boolean> getVerbose();

    @Input
    @Optional
    public abstract MapProperty<String, Object> getEnv();

    @TaskAction
    public void executeContainerCommand() {
        final String containerBinEnvParameter = (String) getProject().getRootProject().getProperties().get("platformlib.container-bin.env-parameter");
        String containerCommand = null;
        if (containerBinEnvParameter != null) {
            final String containerBinEnv = System.getenv(containerBinEnvParameter);
            if (containerBinEnv != null) {
                containerCommand = containerBinEnv;
            }
        }
        if (containerCommand == null) {
            final String projectSpecifiedContainerCommand = (String) getProject().getRootProject().getProperties().get("platformlib.container-bin.command");
            if (projectSpecifiedContainerCommand != null) {
                containerCommand = projectSpecifiedContainerCommand;
            } else {
                final String envContainerBinCommand = System.getenv("PLATFORMLIB_CONTAINER_BIN_COMMAND");
                if (envContainerBinCommand != null) {
                    containerCommand = envContainerBinCommand;
                } else {
                    PlatformLibContainerExtension platformLibContainerExtension = getProject().getRootProject().getExtensions().findByType(PlatformLibContainerExtension.class);
                    if (platformLibContainerExtension == null) {
                        getProject().getRootProject().getExtensions().add(PlatformLibContainerExtension.EXTENSION_NAME, new PlatformLibContainerExtension());
                    }
                    containerCommand = PlatformLibContainerUtility.detectContainerCommand(getContainerCommandState());
                }
            }
        }
        final List<Object> containerCommandAndArguments = new ArrayList<>(Collections.singletonList(containerCommand));
        if (getEnv().isPresent()) {
            getEnv().get().forEach((k, v) -> {
                containerCommandAndArguments.add("-e");
                containerCommandAndArguments.add(k + "=" + v);
            } );
        }
        containerCommandAndArguments.addAll(getCommandAndArguments().get());
        try (OsPlatform osPlatform = LocalOsPlatform.getInstance()) {
            final ProcessBuilder processBuilder = osPlatform.newProcessBuilder();
            final String dockerCommandAsString = containerCommandAndArguments.stream().map(Object::toString).collect(Collectors.joining(" "));
            final String taskName = getName();
            final String containerCommandExt = OsPlatforms.getDefaultOsPlatform().getOsFamily() == OsFamily.WINDOWS ? ".bat" : ".sh";
            try {
                getProject().file("build").mkdir();
                //TODO Wrap command line arguments with spaces
                Files.write(getProject().file("build/docker-command-" + taskName + containerCommandExt).toPath(), dockerCommandAsString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (final IOException ioException) {
                throw new GradleException("Unable to write docker command to file", ioException);
            }
            if (getWorkDir().isPresent()) {
                processBuilder.workDirectory(getWorkDir().get());
            }
            processBuilder.commandAndArguments(containerCommandAndArguments.toArray());
            configureLoggingAndExecute(processBuilder, () -> dockerCommandAsString);
        }
    }

    private void configureLoggingAndExecute(final ProcessBuilder processBuilder, final Supplier<String> dockerCommandAsStringSupplier) {
        final ProcessInstance processInstance = executeContainerCommand(processBuilder, dockerCommandAsStringSupplier);
        if (processInstance.getExitCode() != 0) {
            fail(processInstance, dockerCommandAsStringSupplier.get());
        }
    }

    private ProcessInstance executeContainerCommand(final ProcessBuilder processBuilder, final Supplier<String> dockerCommandAsStringSupplier) {
        processBuilder.logger(action -> action.logger(LOGGER));
        if (getVerbose().getOrElse(false)) {
            processBuilder.stdOutConsumer(getProject().getLogger()::lifecycle);
            processBuilder.stdErrConsumer(getProject().getLogger()::lifecycle);
        } else if (!LOGGER.isDebugEnabled() && LOGGER.isInfoEnabled()) {
            processBuilder.stdOutConsumer(getProject().getLogger()::info);
            processBuilder.stdErrConsumer(getProject().getLogger()::info);
        }
        return processBuilder
                .processInstance(ProcessOutputConfigurator::unlimited)
                .build()
                .execute()
                .toCompletableFuture()
                .join();
    }

    private void fail(ProcessInstance processInstance, final String dockerCommandAsString) {
        getProject().getLogger().error("The container command: {}", dockerCommandAsString);
        getProject().getLogger().error("The container command stdOut: {}", processInstance.getStdOut());
        getProject().getLogger().error("The container command stdErr: {}", processInstance.getStdErr());
        throw new GradleException("The container command execution failed [exit code " + processInstance.getExitCode() + "]");
    }

    private ContainerCommandState getContainerCommandState() {
        return (ContainerCommandState) getProject().getRootProject().getExtensions().getExtraProperties().get(PlatformLibGradleContainerPlugin.CONTAINER_COMMAND_STATE_EXT_PROPERTY_NAME);
    }

}
