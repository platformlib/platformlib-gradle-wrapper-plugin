package com.platformlib.plugins.gradle.wrapper.task

import com.platformlib.plugins.gradle.wrapper.gradle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class DockerTaskTest {
    @Test
    fun testSingleDockerTask() {
        assertThat(gradle("single-docker-task").gradleProjectDir.resolve("build/go-distribution/go-hello-world")).exists()
    }

    @Test
    fun testEnvDockerTask() {
        assertThat(gradle("env-docker-task") {
            tasks = listOf("echo")
        }.gradleProcessInstance.stdOut.contains("Every method has to be covered by test"))
    }

}