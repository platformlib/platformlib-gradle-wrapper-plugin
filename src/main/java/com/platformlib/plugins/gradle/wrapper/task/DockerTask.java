package com.platformlib.plugins.gradle.wrapper.task;

import com.platformlib.os.api.OsPlatform;
import com.platformlib.os.api.enums.OsFamily;
import com.platformlib.os.api.factory.OsPlatforms;
import com.platformlib.os.api.osi.posix.PosixOsInterface;
import com.platformlib.os.api.osi.posix.PosixOsUser;
import com.platformlib.os.local.LocalOsPlatform;
import com.platformlib.plugins.gradle.wrapper.extension.PlatformLibDockerWrapperExtension;
import com.platformlib.process.api.ProcessInstance;
import com.platformlib.process.builder.ProcessBuilder;
import com.platformlib.process.configurator.ProcessOutputConfigurator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Task to be executed in docker container.
 */
public class DockerTask extends DefaultTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTask.class);
    private String image;
    private Collection<Object> dockerOptions = new ArrayList<>();
    private String workDir;
    private Collection<Object> bindMounts = new ArrayList<>();
    private Collection<Object> commandAndArguments = new ArrayList<>();
    private Map<String, Object> env = new HashMap<>();
    private Boolean verbose = true;

    public DockerTask() {
        final PlatformLibDockerWrapperExtension platformLibDockerWrapperExtension = (PlatformLibDockerWrapperExtension) getProject().getExtensions().findByName("platformDockerWrapper");
        if (platformLibDockerWrapperExtension != null) {
            image = platformLibDockerWrapperExtension.getImage();
            dockerOptions.addAll(platformLibDockerWrapperExtension.getDockerOptions());
            workDir = platformLibDockerWrapperExtension.getWorkDir();
            bindMounts.addAll(platformLibDockerWrapperExtension.getBindMounts());
            commandAndArguments.addAll(platformLibDockerWrapperExtension.getCommandAndArguments());
            env.putAll(platformLibDockerWrapperExtension.getEnv());
        }
    }

    public void setImage(final String image) {
        this.image = image;
    }

    public void setWorkDir(final String workDir) {
        this.workDir = workDir;
    }

    public void setDockerOptions(final Collection<Object> dockerOptions) {
        this.dockerOptions = dockerOptions;
    }

    public void setBindMounts(final Collection<Object> bindMounts) {
        this.bindMounts = bindMounts;
    }

    public void setCommandAndArguments(final Collection<Object> commandAndArguments) {
        this.commandAndArguments = commandAndArguments;
    }

    public void setEnv(final Map<String, Object> env) {
        this.env = env;
    }

    public void setVerbose(final Boolean verbose) {
        this.verbose = verbose;
    }

    @Input
    @Optional
    public Boolean isVerbose() {
        return verbose;
    }

    @Input
    @Optional
    public Map<String, Object> getEnv() {
        return env;
    }

    @Input
    public String getImage() {
        return image;
    }

    @Input
    @Optional
    public Collection<Object> getDockerOptions() {
        return dockerOptions;
    }

    @Input
    @Optional
    public String getWorkDir() {
        return workDir;
    }

    @Input
    @Optional
    public Collection<Object> getBindMounts() {
        return bindMounts;
    }

    @Input
    @Optional
    public Collection<Object> getCommandAndArguments() {
        return commandAndArguments;
    }

    @TaskAction
    public void executeInDockerContainer() {
        String dockerCommand = "docker";
        final String dockerBinEnvParameter = (String) getProject().getRootProject().getProperties().get("platformlib.docker-bin-env-parameter");
        if (dockerBinEnvParameter != null) {
            final String dockerBinEnv = System.getenv(dockerBinEnvParameter);
            if (dockerBinEnv != null) {
                dockerCommand = dockerBinEnv;
            }
        }
        final List<String> dockerCommandAndArguments = new ArrayList<>(Arrays.asList(dockerCommand, "container", "run", "--rm"));
        try (OsPlatform osPlatform = LocalOsPlatform.getInstance()) {
            if (osPlatform.getOsFamily() == OsFamily.UNIX) {
                final PosixOsUser posixOsUser = osPlatform.getTypedOsInterface(PosixOsInterface.class).getCurrentUser();
                dockerCommandAndArguments.addAll(Arrays.asList("-u", String.format("%d:%d", posixOsUser.getUser().getId(), posixOsUser.getPrimaryGroup().getId())));
            }
            bindMounts.forEach(bindMount -> {
                final String bindMountAsString = bindMount.toString();
                //TODO Check how does docker split -v bind directories on windows
                final int fromIndex = osPlatform.getOsFamily() == OsFamily.WINDOWS ? 2 : 0; //Ignore <Driver>:
                final int delimiterIndex = bindMountAsString.indexOf(":", fromIndex);
                if (delimiterIndex == -1) {
                    throw new GradleException("Unable to parse bind option '" + bindMountAsString + "'");
                }
                final Path bindMountPath = Paths.get(bindMountAsString.substring(0, delimiterIndex));
                if (!Files.isDirectory(bindMountPath)) {
                    try {
                        LOGGER.info("Create bind directory {}", bindMountPath);
                        Files.createDirectories(bindMountPath);
                    } catch (final IOException ioException) {
                        throw new GradleException("Fail to create bind directory " + bindMountPath, ioException);
                    }
                }
                dockerCommandAndArguments.addAll(Arrays.asList("-v", bindMountAsString));
            });
            if (workDir != null) {
                dockerCommandAndArguments.addAll(Arrays.asList("--workdir", workDir));
            }
            dockerCommandAndArguments.addAll(dockerOptions.stream().map(Objects::toString).collect(Collectors.toList()));
            if (!env.isEmpty()) {
                env.forEach((k, v) -> {
                    dockerCommandAndArguments.add("-e");
                    dockerCommandAndArguments.add(k + "=" + v);
                } );
            }
            dockerCommandAndArguments.add(image);
            dockerCommandAndArguments.addAll(commandAndArguments.stream().map(Objects::toString).collect(Collectors.toList()));
            final ProcessBuilder processBuilder = osPlatform.newProcessBuilder();
            final String dockerCommandAsString = String.join(" ", dockerCommandAndArguments);
            final String taskName = getName();
            final String dockerCommandExt = OsPlatforms.getDefaultOsPlatform().getOsFamily() == OsFamily.WINDOWS ? ".bat" : ".sh";
            try {
                getProject().file("build").mkdir();
                //TODO Wrap command line arguments with spaces
                Files.write(getProject().file("build/docker-command-" + taskName + dockerCommandExt).toPath(), dockerCommandAsString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (final IOException ioException) {
                throw new GradleException("Unable to write docker command to file", ioException);
            }
            processBuilder.commandAndArguments(dockerCommandAndArguments.toArray());
            configureLoggingAndExecute(processBuilder, () -> dockerCommandAsString);
        }
    }

    //@Internal
    public void pullImage() {
        getLogger().lifecycle("Pull docker image {}", image);
        final List<String> dockerPull = new ArrayList<>(Arrays.asList("docker", "pull", image));
        final List<String> dockerQuitePull = new ArrayList<>(Arrays.asList("docker", "pull", "--quiet", image));
        try (OsPlatform osPlatform = LocalOsPlatform.getInstance()) {
            final ProcessInstance dockerQuiteProcessInstance = executeDockerCommand(osPlatform.newProcessBuilder().commandAndArguments(dockerQuitePull.toArray()), () -> String.join(" ", dockerQuitePull));
            if (dockerQuiteProcessInstance.getExitCode() != 0) {
                //--quiet option is available since 19.03, rerun if error because of unknown flag
                if (dockerQuiteProcessInstance.getStdErr().stream().anyMatch(stdErrLine -> stdErrLine.contains("unknown flag: --quiet"))) {
                    configureLoggingAndExecute(osPlatform.newProcessBuilder().commandAndArguments(dockerPull.toArray()), () -> String.join(" ", dockerPull));
                } else {
                    fail(dockerQuiteProcessInstance, String.join(" ", dockerQuitePull));
                }
            }
        }
    }

    private void configureLoggingAndExecute(final ProcessBuilder processBuilder, final Supplier<String> dockerCommandAsStringSupplier) {
        final ProcessInstance processInstance = executeDockerCommand(processBuilder, dockerCommandAsStringSupplier);
        if (processInstance.getExitCode() != 0) {
            fail(processInstance, dockerCommandAsStringSupplier.get());
        }
    }

    private ProcessInstance executeDockerCommand(final ProcessBuilder processBuilder, final Supplier<String> dockerCommandAsStringSupplier) {
        processBuilder.logger(action -> action.logger(LOGGER));
        if (verbose) {
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
        getProject().getLogger().error("The docker command: {}", dockerCommandAsString);
        getProject().getLogger().error("The docker command stdOut: {}", processInstance.getStdOut());
        getProject().getLogger().error("The docker command stdErr: {}", processInstance.getStdErr());
        throw new GradleException("The docker command execution failed [exit code " + processInstance.getExitCode() + "]");
    }
}
