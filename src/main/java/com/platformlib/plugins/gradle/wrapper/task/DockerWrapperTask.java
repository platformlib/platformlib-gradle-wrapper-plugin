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
import org.gradle.internal.jvm.Jvm;

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

public class DockerWrapperTask extends AbstractWrapperTask<DockerGradleWrapperConfiguration> {
    private String dockerHostEndpoint = "/platform-gradle-wrapper";
    private String projectSource = "project-source";

    @Override
    protected void executeWrappedGradle(final Collection<String> gradleCommandAndArguments) {
        final Path buildPath = getBaseBuildPath().toPath().resolve(getId());

        getLogger().debug("Synchronize project sources");
        getProject().sync(it -> {
            it.from (getProject().getRootProject().getProjectDir());
            it.into(buildPath.resolve(projectSource).toFile());
            it.exclude("**/build", ".gradle", ".idea", ".git");
        });

        getLogger().debug("Synchronize .gradle directory");
        getProject().sync(it -> {
            it.from (getProject().getRootProject().getGradle().getGradleUserHomeDir());
            it.into(buildPath.resolve(".gradle").toFile());
            it.include("*.*", "wrapper/dists/*-" + getProject().getRootProject().getGradle().getGradleVersion() +"-bin/**/*");
        });

        final String dockerHostJavaHome;
        if (getConfiguration().isUseCurrentJava()) {
            getLogger().debug("Synchronize current java to use in docker");
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

        final Path m2RepositoryPath = buildPath.resolve(".m2/repository");
        getProject().mkdir(m2RepositoryPath.toFile());

        getConfiguration().getM2Artifacts().forEach(m2Artifact -> {
            getLogger().debug("Synchronize m2 {}", m2Artifact);
            final String target = m2Artifact.replaceAll("[.]+", "/");
            final Path sourcePath = Paths.get(System.getProperty("user.home")).resolve(".m2/repository").resolve(target);
            getProject().sync(it -> {
                it.from (sourcePath.toFile(), fromAction -> fromAction.into(target));
                it.into(m2RepositoryPath.toFile());
                it.setFileMode(0700);
            });
        });

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
            dockerCommandAndArguments.addAll(Arrays.asList("-v", buildPath.toString() + ":" + dockerHostEndpoint));
            dockerCommandAndArguments.addAll(Arrays.asList("--workdir", dockerHostEndpoint + "/" + projectSource));
            dockerCommandAndArguments.addAll(getConfiguration().getDockerOptions());
            dockerCommandAndArguments.add(getConfiguration().getImage());

            dockerCommandAndArguments.addAll(Arrays.asList("./gradlew", "--no-daemon", "--gradle-user-home", dockerHostEndpoint + "/.gradle", "-Duser.home=" + dockerHostEndpoint));
            dockerCommandAndArguments.addAll(gradleCommandAndArguments);
            final ProcessBuilder processBuilder = osPlatform.newProcessBuilder();
            if (getConfiguration().isDryRun()) {
                processBuilder.dryRun(action -> action.commandAndArgumentsSupplier(commandAndArguments -> getProject().getLogger().quiet("The wrapped command to execute: " + String.join(" ", commandAndArguments))));
            }
            final String dockerCommandAsString = String.join(" ", dockerCommandAndArguments);
            try {
                Files.write(getProject().file(buildPath.resolve("docker-command.txt").toFile()).toPath(), dockerCommandAsString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (final IOException ioException) {
                throw new GradleException("Unable to write docker command to file", ioException);
            }
            processBuilder.commandAndArguments(dockerCommandAndArguments.toArray());
            processBuilder.logger(action -> action.logger(getProject().getLogger()));
            if (!getProject().getLogger().isDebugEnabled()) {
                processBuilder.stdOutConsumer(System.out::println);
                processBuilder.stdErrConsumer(System.err::println);
            }
            final ProcessInstance processInstance = processBuilder.build().execute().toCompletableFuture().join();
            if (processInstance.getExitCode() != 0) {
                getProject().getLogger().lifecycle("The docker command: {}", dockerCommandAsString);
                throw new GradleException("The docker command execution failed [exit code " + processInstance.getExitCode() + "]");
            }
        }
    }
}
