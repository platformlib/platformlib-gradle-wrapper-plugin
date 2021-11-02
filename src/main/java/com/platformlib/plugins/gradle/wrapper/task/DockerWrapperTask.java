package com.platformlib.plugins.gradle.wrapper.task;

import com.platformlib.os.api.OsPlatform;
import com.platformlib.os.api.enums.OsFamily;
import com.platformlib.os.api.osi.posix.PosixOsInterface;
import com.platformlib.os.api.osi.posix.PosixOsUser;
import com.platformlib.os.local.LocalOsPlatform;
import com.platformlib.plugins.gradle.wrapper.configuration.DockerGradleWrapperConfiguration;
import com.platformlib.process.api.ProcessInstance;
import com.platformlib.process.builder.ProcessBuilder;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.internal.jvm.Jvm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Task to build gradle project in docker container.
 */
public class DockerWrapperTask extends AbstractWrapperTask<DockerGradleWrapperConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerWrapperTask.class);
    private String dockerHostEndpoint = "/platform-gradle-wrapper";
    private String projectSource = "project-source";
    private File baseBuildPath;


    @Override
    protected void executeWrappedGradle(final Collection<String> gradleCommandAndArguments) {
        final Path buildPath = getBaseBuildPath().toPath().resolve(getId());

        LOGGER.debug("Synchronize project sources");
        getProject().sync(it -> {
            it.from (getProject().getRootProject().getProjectDir());
            it.into(buildPath.resolve(projectSource).toFile());
            it.exclude("**/build", ".gradle", ".idea", ".git");
            it.exclude(spec -> spec.getFile().equals(getBaseBuildPath()));
        });

        LOGGER.debug("Synchronize ~/.gradle directory");
        getProject().sync(it -> {
            it.from (getProject().getRootProject().getGradle().getGradleUserHomeDir());
            it.into(buildPath.resolve(".gradle").toFile());
            it.include("*.*", "wrapper/dists/*-" + getProject().getRootProject().getGradle().getGradleVersion() +"-bin/**/*");
        });

        final String dockerHostJavaHome;
        if (getConfiguration().isUseCurrentJava()) {
            LOGGER.info("Synchronize current java to use in docker");
            getProject().sync(it -> {
                it.from (Jvm.current().getJavaHome());
                it.into(buildPath.resolve("jdk").toFile());
                it.exclude("db", "*/javaws", "lib/missioncontrol", "lib/visualvm", "src.zip", "javafx-src.zip");
                it.setFileMode(0700);
            });
            dockerHostJavaHome = dockerHostEndpoint + "/jdk";
        } else {
            dockerHostJavaHome = getConfiguration().getJavaHome();
        }


        final boolean isLocalMavenRepositoryRedefined = System.getProperty("maven.repo.local") != null;
        final Path localM2RepositoryPath = Paths.get(isLocalMavenRepositoryRedefined ? System.getProperty("maven.repo.local") : (System.getProperty( "user.home") + "/.m2/repository"));
        final String dockerM2Repository = isLocalMavenRepositoryRedefined ? ".m2-repository-redefined" : ".m2/repository";
        if (!getConfiguration().getM2Artifacts().isEmpty()) {
            final Path dockerM2RepositoryPath = buildPath.resolve(dockerM2Repository);
            getConfiguration().getM2Artifacts().forEach(m2Artifact -> {
                getProject().mkdir(dockerM2RepositoryPath.toFile());
                LOGGER.debug("Synchronize m2 {}", m2Artifact);
                final String target = m2Artifact.replaceAll("[.]+", "/");
                getProject().sync(it -> {
                    it.from (localM2RepositoryPath.resolve(target).toFile(), fromAction -> fromAction.into(target));
                    it.into(dockerM2RepositoryPath.toFile());
                    it.setFileMode(0700);
                });
            });
        }

        final List<String> dockerCommandAndArguments = new ArrayList<>(Arrays.asList("docker", "container", "run", "--rm"));
        try (OsPlatform osPlatform = LocalOsPlatform.getInstance()) {
            if (osPlatform.getOsFamily() == OsFamily.UNIX) {
                final PosixOsUser posixOsUser = osPlatform.getTypedOsInterface(PosixOsInterface.class).getCurrentUser();
                dockerCommandAndArguments.addAll(Arrays.asList("-u", String.format("%d:%d", posixOsUser.getUser().getId(), posixOsUser.getPrimaryGroup().getId())));
            }
            dockerCommandAndArguments.addAll(Arrays.asList("--env", "GRADLE_OPTS=-Duser.home=" + dockerHostEndpoint));
            dockerCommandAndArguments.addAll(Arrays.asList("--env", "HOME=" + dockerHostEndpoint));
            if (dockerHostJavaHome != null) {
                dockerCommandAndArguments.addAll(Arrays.asList("--env", "JAVA_HOME=" + dockerHostJavaHome));
            }
            dockerCommandAndArguments.addAll(Arrays.asList("-v", buildPath + ":" + dockerHostEndpoint));
            if (getConfiguration().getWorkDir() != null) {
                dockerCommandAndArguments.addAll(Arrays.asList("--workdir", getConfiguration().getWorkDir()));
            } else {
                dockerCommandAndArguments.addAll(Arrays.asList("--workdir", dockerHostEndpoint + "/" + projectSource));
            }
            if (isLocalMavenRepositoryRedefined || getConfiguration().isBindLocalM2Repository() && getConfiguration().getM2Artifacts().isEmpty()) {
                try {
                    Files.createDirectories(localM2RepositoryPath);
                } catch (final IOException ioException) {
                    throw new GradleException("Fail to create local M2 repository bind entry", ioException);
                }
                dockerCommandAndArguments.addAll(Arrays.asList("-v", localM2RepositoryPath + ":" + dockerHostEndpoint + "/" + dockerM2Repository));
            }
            dockerCommandAndArguments.addAll(getConfiguration().getDockerOptions());
            dockerCommandAndArguments.add(getConfiguration().getImage());

            dockerCommandAndArguments.addAll(Arrays.asList("./gradlew", "--no-daemon", "--gradle-user-home", dockerHostEndpoint + "/.gradle", "-Duser.home=" + dockerHostEndpoint));
            dockerCommandAndArguments.addAll(gradleCommandAndArguments.stream().map(argument -> {
                if (isLocalMavenRepositoryRedefined && argument.startsWith("-Dmaven.repo.local=")) {
                    return "-Dmaven.repo.local=" + dockerM2Repository;
                } else {
                    return argument;
                }
            }).collect(Collectors.toList()));
            final ProcessBuilder processBuilder = osPlatform.newProcessBuilder();
            if (getConfiguration().isDryRun()) {
                processBuilder.dryRun(action -> action.commandAndArgumentsSupplier(commandAndArguments -> LOGGER.info("The wrapped command to execute: " + String.join(" ", commandAndArguments))));
            }
            final String dockerCommandAsString = String.join(" ", dockerCommandAndArguments);
            final String dockerCommandExt = osPlatform.getOsFamily() == OsFamily.UNIX ? ".sh" : ".bat";
            try {
                Files.write(getProject().file(buildPath.resolve("docker-command" + dockerCommandExt).toFile()).toPath(), dockerCommandAsString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (final IOException ioException) {
                throw new GradleException("Unable to write docker command to file", ioException);
            }
            processBuilder.commandAndArguments(dockerCommandAndArguments.toArray());
            processBuilder.logger(action -> action.logger(LOGGER));
            if (!LOGGER.isDebugEnabled()) {
                processBuilder.stdOutConsumer(getProject().getLogger()::lifecycle);
                processBuilder.stdErrConsumer(getProject().getLogger()::lifecycle);
            }
            final ProcessInstance processInstance = processBuilder.build().execute().toCompletableFuture().join();
            if (processInstance.getExitCode() != 0) {
                getProject().getLogger().lifecycle("The docker command: {}", dockerCommandAsString);
                throw new GradleException("The docker command execution failed [exit code " + processInstance.getExitCode() + "]");
            }
        }
    }

    @InputDirectory
    public File getBaseBuildPath() {
        return baseBuildPath;
    }

    public void setBaseBuildPath(final File baseBuildPath) {
        this.baseBuildPath = baseBuildPath;
    }

}
