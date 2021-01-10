package com.platformlib.plugins.gradle.wrapper;

import com.platformlib.plugins.gradle.wrapper.task.AbstractWrapperTask;
import com.platformlib.plugins.gradle.wrapper.task.DockerWrapperTask;
import com.platformlib.plugins.gradle.wrapper.task.SshWrapperTask;
import com.platformlib.plugins.gradle.wrapper.utility.PlatformLibGradleWrapperUtility;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.tasks.Sync;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PlatformLibGradleWrapperPlugin implements Plugin<Project>  {
    private boolean activated;
    private boolean added;

    @Override
    public void apply(final Project project) {
        if (project.getRootProject() != project) {
            throw new GradleException("The com.platformlib.gradle-wrapper must be applied on root project");
        }
        final PlatformLibGradleWrapperExtension platformLibGradleWrapperExtension = project.getExtensions().create("platformGradleWrapper", PlatformLibGradleWrapperExtension.class);
        platformLibGradleWrapperExtension.setEnabled(!"false".equals(project.findProperty(PlatformLibGradleWrapperExtension.GRADLE_WRAPPER_PLUGIN_PROJECT_PROPERTY)));

        final List<String> tasksToWrap = new ArrayList<>(project.getGradle().getStartParameter().getTaskNames());
        final Task platformGradleWrapProcessResourcesTask = project.task(Collections.singletonMap("type", Sync.class), "platformGradleWrapperProcessResources");
        final Task platformGradleWrapAggregationTask = project.task("platformGradleWrapperAggregation"); //Aggregation task
        platformGradleWrapAggregationTask.dependsOn(platformGradleWrapProcessResourcesTask);
        project.afterEvaluate(evaluatedProject -> {
            if (!platformLibGradleWrapperExtension.isEnabled()) {
                return;
            }
            final Collection<String> unwrappedTasks = PlatformLibGradleWrapperUtility.toStringCollection(platformLibGradleWrapperExtension.getUnwrappedTasks());
            tasksToWrap.removeAll(unwrappedTasks);
            platformLibGradleWrapperExtension.getDocker().forEach(dockerWrapperConfiguration -> {
                if (dockerWrapperConfiguration.getActivateBy().get()) {
                    project.getRootProject().getLogger().quiet("Wrap gradle execution in '{}' docker configuration", dockerWrapperConfiguration.getName());
                    activated = true;
                }
            });

            if (!activated) {
                project.getLogger().quiet("Gradle execution wrapping is not activated");
                return;
            }
            evaluatedProject.getGradle().afterProject(afterProject -> {
                if (!added) {
                    for (final Task task: afterProject.getTasks()) {
                        if (tasksToWrap.contains(task.getName())) {
                            project.getLogger().quiet("Mark {} as dependent on wrap task {}", task, platformGradleWrapAggregationTask);
                            task.dependsOn(platformGradleWrapAggregationTask);
                            added = true;
                            break;
                        }
                    }
                    if (added) {
                        final File baseBuildPath = project.mkdir(project.file("build/platform-gradle-wrapper"));
                        platformLibGradleWrapperExtension.getDocker().forEach(dockerConfiguration -> {
                            if (dockerConfiguration.getActivateBy().get()) {
                                final DockerWrapperTask dockerWrapperTask = (DockerWrapperTask) project.task(Collections.singletonMap("type", DockerWrapperTask.class), "docker-wrapper-" + dockerConfiguration.getName());
                                dockerWrapperTask.dependsOn(platformGradleWrapProcessResourcesTask);
                                dockerWrapperTask.setBaseBuildPath(baseBuildPath);
                                dockerWrapperTask.setId(dockerConfiguration.getName());
                                dockerWrapperTask.setConfiguration(dockerConfiguration);
                                platformGradleWrapAggregationTask.dependsOn(dockerWrapperTask);
                            } else {
                                project.getLogger().debug("Skip docker wrapping {} because of inactivation", dockerConfiguration.getName());
                            }
                        });
                        platformLibGradleWrapperExtension.getSsh().forEach(sshConfiguration -> {
                            if (sshConfiguration.getActivateBy().get()) {
                                final SshWrapperTask sshWrapperTask = (SshWrapperTask) project.task(Collections.singletonMap("type", SshWrapperTask.class), "ssh-wrapper-" + sshConfiguration.getName());
                                sshWrapperTask.dependsOn(platformGradleWrapProcessResourcesTask);
                                sshWrapperTask.setId(sshConfiguration.getName());
                                sshWrapperTask.setConfiguration(sshConfiguration);
                                sshWrapperTask.setBaseBuildPath(baseBuildPath);
                                platformGradleWrapAggregationTask.dependsOn(sshWrapperTask);
                            } else {
                                project.getLogger().debug("Skip ssh wrapping {} because of inactivation", sshConfiguration.getName());
                            }
                        });
                    }
                }
            });
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
        project.getLogger().debug("Disable {}", task);
        task.setEnabled(false);
        executionGraph.getDependencies(task).forEach(t -> disable(aggregationTask, project, executionGraph, t));
    }
}
