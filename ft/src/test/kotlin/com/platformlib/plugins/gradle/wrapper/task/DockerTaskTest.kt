package com.platformlib.plugins.gradle.wrapper.task

import com.platformlib.plugins.gradle.wrapper.gradle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DockerTaskTest {
    @Test
    fun testSingleDockerTask() {
        assertThat(gradle("single-docker-task").gradleProjectDir.resolve("build/go-distribution/go-hello-world")).exists()
    }
}