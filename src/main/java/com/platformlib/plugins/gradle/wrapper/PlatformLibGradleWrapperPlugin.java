package com.platformlib.plugins.gradle.wrapper;

import com.platformlib.plugins.gradle.wrapper.extension.PlatformLibDockerWrapperExtension;
import com.platformlib.plugins.gradle.wrapper.extension.PlatformLibGradleWrapperExtension;
import com.platformlib.plugins.gradle.wrapper.task.AbstractWrapperTask;
import com.platformlib.plugins.gradle.wrapper.task.DockerWrapperTask;
import com.platformlib.plugins.gradle.wrapper.task.SshWrapperTask;
import com.platformlib.plugins.gradle.wrapper.utility.PlatformLibGradleWrapperUtility;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.tasks.Sync;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PlatformLibGradleWrapperPlugin implements Plugin<Project>  {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformLibGradleWrapperPlugin.class);
    private boolean activated;
    private boolean added;
    private File dockerBaseBuildPath = null;

    @Override
    public void apply(final Project project) {
        project.getExtensions().create("platformDockerWrapper", PlatformLibDockerWrapperExtension.class);
        if (project.getRootProject() != project) {
            LOGGER.debug("Skip configuring platform gradle wrapper because of non root project");
            return;
        }
        if (GradleVersion.current().compareTo(GradleVersion.version("6.0")) < 0) {
            LOGGER.debug("Only Docker task is available because of used gradle version {}", GradleVersion.current());
            return;
        }
        final PlatformLibGradleWrapperExtension platformLibGradleWrapperExtension = project.getExtensions().create("platformGradleWrapper", PlatformLibGradleWrapperExtension.class);
        platformLibGradleWrapperExtension.setEnabled(!"false".equals(project.findProperty(PlatformLibGradleWrapperExtension.GRADLE_WRAPPER_PLUGIN_PROJECT_PROPERTY)));

        final List<String> tasksToWrap = new ArrayList<>(project.getGradle().getStartParameter().getTaskNames());
        final Task platformGradleWrapProcessResourcesTask = project.task(Collections.singletonMap("type", Sync.class), "platformGradleWrapperProcessResources");
        final Task platformGradleWrapAggregationTask = project.task("platformGradleWrapperAggregation"); //Aggregation task
        project.task("platformGradleWrapperClean", action -> {  //Clean task
            action.doLast(payLoad -> {
                if (dockerBaseBuildPath != null && dockerBaseBuildPath.exists()) {
                    LOGGER.debug("Delete directory {}", dockerBaseBuildPath);
                    action.getProject().getRootProject().delete(dockerBaseBuildPath);
                }
            });
        });
        platformGradleWrapAggregationTask.dependsOn(platformGradleWrapProcessResourcesTask);
        project.afterEvaluate(evaluatedProject -> {
            if (!platformLibGradleWrapperExtension.isEnabled()) {
                //The plugin is being executed with special option, which means tha we are inside container
                return;
            }
            if (platformLibGradleWrapperExtension.getWrapperBaseDir() != null)
                dockerBaseBuildPath = platformLibGradleWrapperExtension.getWrapperBaseDir();
            else {
                dockerBaseBuildPath = project.getRootProject().file(".docker-gradle-wrapper");
            }
            final Collection<String> unwrappedTasks = PlatformLibGradleWrapperUtility.toStringCollection(platformLibGradleWrapperExtension.getUnwrappedTasks());
            tasksToWrap.removeAll(unwrappedTasks);
            platformLibGradleWrapperExtension.getDocker().forEach(dockerWrapperConfiguration -> {
                if (dockerWrapperConfiguration.getActivateBy().get()) {
                    LOGGER.info("Wrap gradle execution in '{}' docker configuration", dockerWrapperConfiguration.getName());
                    activated = true;
                }
            });

            if (!activated) {
                LOGGER.info("Gradle execution wrapping is not activated");
                return;
            }

            if (!added) {
                for (final Task task : evaluatedProject.getTasks()) {
                    if (tasksToWrap.contains(task.getName())) {
                        LOGGER.info("Mark {} as dependent on wrap task {}", task, platformGradleWrapAggregationTask);
                        task.dependsOn(platformGradleWrapAggregationTask);
                        added = true;
                        break;
                    }
                }
                if (added) {
                    platformLibGradleWrapperExtension.getDocker().forEach(dockerConfiguration -> {
                        if (dockerConfiguration.getActivateBy().get()) {
                            project.mkdir(dockerBaseBuildPath);
                            final DockerWrapperTask dockerWrapperTask = (DockerWrapperTask) project.task(Collections.singletonMap("type", DockerWrapperTask.class), "docker-wrapper-" + dockerConfiguration.getName());
                            dockerWrapperTask.dependsOn(platformGradleWrapProcessResourcesTask);
                            dockerWrapperTask.setBaseBuildPath(dockerBaseBuildPath);
                            dockerWrapperTask.setId(dockerConfiguration.getName());
                            dockerWrapperTask.setConfiguration(dockerConfiguration);
                            platformGradleWrapAggregationTask.dependsOn(dockerWrapperTask);
                        } else {
                            LOGGER.info("Skip docker wrapping {} because of inactivation", dockerConfiguration.getName());
                        }
                    });
                    platformLibGradleWrapperExtension.getSsh().forEach(sshConfiguration -> {
                        if (sshConfiguration.getActivateBy().get()) {
                            final SshWrapperTask sshWrapperTask = (SshWrapperTask) project.task(Collections.singletonMap("type", SshWrapperTask.class), "ssh-wrapper-" + sshConfiguration.getName());
                            sshWrapperTask.dependsOn(platformGradleWrapProcessResourcesTask);
                            sshWrapperTask.setId(sshConfiguration.getName());
                            sshWrapperTask.setConfiguration(sshConfiguration);
                            platformGradleWrapAggregationTask.dependsOn(sshWrapperTask);
                        } else {
                            LOGGER.debug("Skip ssh wrapping {} because of inactivation", sshConfiguration.getName());
                        }
                    });
                }
            }
        });

        project.getGradle().getTaskGraph().whenReady(executionGraph -> {
            if (activated) {
                executionGraph.getAllTasks().forEach(task -> {
                    if (tasksToWrap.contains(task.getName())) {
                        disable(platformGradleWrapAggregationTask, project, executionGraph, task);
                    }
                });
            }
        });
    }

    private static void disable(final Task aggregationTask, final Project project, final TaskExecutionGraph executionGraph, final Task task) {
        if (!task.getEnabled() || task == aggregationTask || task instanceof AbstractWrapperTask) {
            return;
        }
        LOGGER.debug("Disable {}", task);
        task.setEnabled(false);
        executionGraph.getDependencies(task).forEach(t -> disable(aggregationTask, project, executionGraph, t));
    }
}
