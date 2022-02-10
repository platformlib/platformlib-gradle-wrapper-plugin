package com.platformlib.plugins.gradle.wrapper.task

import com.platformlib.plugins.gradle.wrapper.gradle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE

class DockerTaskTest {

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    fun testSingleDockerTaskOnGradle4103() {
        assertThat(gradle("single-docker-task-gradle-4.10.3").gradleProjectDir.resolve("build/go-distribution/go-hello-world")).exists()
    }

    @Test
    fun testSingleDockerTask() {
        assertThat(gradle("single-docker-task").gradleProjectDir.resolve("build/go-distribution/go-hello-world")).exists()
    }

    @Test
    fun testEnvDockerTask() {
        assertThat(
            gradle("env-docker-task") {
                tasks = listOf("echo")
            }.gradleProcessInstance.stdOut.contains("Every method has to be covered by test")
        ).isTrue
    }

    @Test
    fun testContainerCommand() {
        assertThat(
            gradle("container-command") {
                tasks = listOf("buildGoApplication")
                expectedFailure = true
            }.gradleProjectDir.resolve("build/docker-command-buildGoApplication.sh")
        ).content().contains("container-2022 container run --rm")
    }

    @Test
    fun testDockerWrapperExtension() {
        val goAppPath = gradle("docker-wrapper-extension").gradleProjectDir.resolve("build/go-distribution")
        assertThat(goAppPath.resolve("app1")).exists()
        assertThat(goAppPath.resolve("app2")).exists()
    }
}