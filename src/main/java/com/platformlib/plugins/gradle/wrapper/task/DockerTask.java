package com.platformlib.plugins.gradle.wrapper.task;

import com.platformlib.os.api.OsPlatform;
import com.platformlib.os.api.enums.OsFamily;
import com.platformlib.os.api.factory.OsPlatforms;
import com.platformlib.os.api.osi.posix.PosixOsInterface;
import com.platformlib.os.api.osi.posix.PosixOsUser;
import com.platformlib.os.local.LocalOsPlatform;
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
import java.util.stream.Collectors;

public class DockerTask extends DefaultTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTask.class);
    private String image;
    private Collection<Object> dockerOptions = new ArrayList<>();
    private String workDir;
    private Collection<Object> bindMounts = new ArrayList<>();
    private String bindDirectory;
    private Collection<Object> commandAndArguments = new ArrayList<>();
    private Map<String, Object> env = new HashMap<>();
    private Boolean verbose = true;

    public void setImage(final String image) {
        this.image = image;
    }

    public void setWorkDir(final String workDir) {
        this.workDir = workDir;
    }

    public void setBindDirectory(final String bindDirectory) {
        this.bindDirectory = bindDirectory;
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
    public String getBindDirectory() {
        return bindDirectory;
    }

    @Input
    @Optional
    public Collection<Object> getCommandAndArguments() {
        return commandAndArguments;
    }

    @TaskAction
    public void execute() {
        final List<String> dockerCommandAndArguments = new ArrayList<>(Arrays.asList("docker", "container", "run", "--rm"));
        try (OsPlatform osPlatform = LocalOsPlatform.getInstance()) {
            if (osPlatform.getOsFamily() == OsFamily.UNIX) {
                final PosixOsUser posixOsUser = osPlatform.getTypedOsInterface(PosixOsInterface.class).getCurrentUser();
                dockerCommandAndArguments.addAll(Arrays.asList("-u", String.format("%d:%d", posixOsUser.getUser().getId(), posixOsUser.getPrimaryGroup().getId())));
            }
            bindMounts.forEach(bindMount -> {
                final String bindMountAsString = bindMount.toString();
                final Path bindMountPath = Paths.get(bindMountAsString.split(":")[0]);
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
            processBuilder.logger(action -> action.logger(LOGGER));
            if (verbose) {
                processBuilder.stdOutConsumer(getProject().getLogger()::lifecycle);
                processBuilder.stdErrConsumer(getProject().getLogger()::lifecycle);
            } else if (!LOGGER.isDebugEnabled() && LOGGER.isInfoEnabled()) {
                processBuilder.stdOutConsumer(getProject().getLogger()::info);
                processBuilder.stdErrConsumer(getProject().getLogger()::info);
            }
            final ProcessInstance processInstance = processBuilder
                    .processInstance(ProcessOutputConfigurator::unlimited)
                    .build()
                    .execute()
                    .toCompletableFuture()
                    .join();
            if (processInstance.getExitCode() != 0) {
                getProject().getLogger().error("The docker command: {}", dockerCommandAsString);
                getProject().getLogger().error("The docker command stdOut: {}", processInstance.getStdOut());
                getProject().getLogger().error("The docker command stdErr: {}", processInstance.getStdErr());
                throw new GradleException("The docker command execution failed [exit code " + processInstance.getExitCode() + "]");
            }
        }
    }
}
