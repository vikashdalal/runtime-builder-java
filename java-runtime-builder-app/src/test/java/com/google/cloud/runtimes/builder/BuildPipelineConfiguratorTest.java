/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.runtimes.builder;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.runtimes.builder.TestUtils.TestWorkspaceBuilder;
import com.google.cloud.runtimes.builder.buildsteps.GradleBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.MavenBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.ScriptExecutionBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.base.BuildStep;
import com.google.cloud.runtimes.builder.buildsteps.base.BuildStepFactory;
import com.google.cloud.runtimes.builder.buildsteps.docker.StageDockerArtifactBuildStep;
import com.google.cloud.runtimes.builder.config.AppYamlFinder;
import com.google.cloud.runtimes.builder.config.AppYamlParser;
import com.google.cloud.runtimes.builder.config.YamlParser;
import com.google.cloud.runtimes.builder.config.domain.AppYaml;
import com.google.cloud.runtimes.builder.config.domain.RuntimeConfig;
import com.google.cloud.runtimes.builder.exception.AppYamlNotFoundException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Unit tests for {@link BuildPipelineConfigurator}
 */
public class BuildPipelineConfiguratorTest {

  @Mock private BuildStepFactory buildStepFactory;
  @Mock private MavenBuildStep mavenBuildStep;
  @Mock private GradleBuildStep gradleBuildStep;
  @Mock private StageDockerArtifactBuildStep stageDockerArtifactBuildStep;
  @Mock private ScriptExecutionBuildStep scriptExecutionBuildStep;

  // use the actual yaml parser and yaml finders instead of mocks
  private YamlParser<AppYaml> appYamlYamlParser = new AppYamlParser();
  private AppYamlFinder appYamlFinder = new AppYamlFinder(Optional.empty());
  private BuildPipelineConfigurator buildPipelineConfigurator;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    when(buildStepFactory.createMavenBuildStep()).thenReturn(mavenBuildStep);
    when(buildStepFactory.createGradleBuildStep()).thenReturn(gradleBuildStep);
    when(buildStepFactory.createStageDockerArtifactBuildStep(any(RuntimeConfig.class)))
        .thenReturn(stageDockerArtifactBuildStep);
    when(buildStepFactory.createScriptExecutionBuildStep(anyString()))
        .thenReturn(scriptExecutionBuildStep);

    buildPipelineConfigurator
        = new BuildPipelineConfigurator(appYamlYamlParser, appYamlFinder, buildStepFactory);
  }

  @Test
  public void test_simpleWorkspace() throws AppYamlNotFoundException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("foo.war").build()
        .file("app.yaml").withContents("env: flex\nruntime: java").build()
        .build();

    List<BuildStep> buildSteps = buildPipelineConfigurator.configurePipeline(workspace);
    assertEquals(1, buildSteps.size());
    assertEquals(stageDockerArtifactBuildStep, buildSteps.get(0));
  }

  @Test
  public void test_mavenWorkspace() throws AppYamlNotFoundException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .file("src/main/appengine/app.yaml").withContents("env: flex\nruntime: java").build()
        .build();

    List<BuildStep> buildSteps = buildPipelineConfigurator.configurePipeline(workspace);
    assertEquals(2, buildSteps.size());
    assertEquals(mavenBuildStep, buildSteps.get(0));
    assertEquals(stageDockerArtifactBuildStep, buildSteps.get(1));
  }

  @Test
  public void test_mavenAndGradleWorkspace() throws AppYamlNotFoundException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .file("build.gradle").build()
        .file("src/main/appengine/app.yaml").withContents("env: flex\nruntime: java").build()
        .build();

    List<BuildStep> buildSteps = buildPipelineConfigurator.configurePipeline(workspace);
    assertEquals(2, buildSteps.size());
    // maven takes precedence
    assertEquals(mavenBuildStep, buildSteps.get(0));
    assertEquals(stageDockerArtifactBuildStep, buildSteps.get(1));
  }

  @Test
  public void test_mavenWorkspace_customArtifact() throws AppYamlNotFoundException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .file("src/main/appengine/app.yaml")
            .withContents(
                "runtime: java\n" +
                "env: flex\n" +
                "runtime_config:\n" +
                "  artifact: my_output_dir/artifact.jar\n").build()
        .build();

    List<BuildStep> buildSteps = buildPipelineConfigurator.configurePipeline(workspace);
    assertEquals(2, buildSteps.size());
    assertEquals(mavenBuildStep, buildSteps.get(0));
    assertEquals(stageDockerArtifactBuildStep, buildSteps.get(1));
    verify(stageDockerArtifactBuildStep, times(1))
        .setArtifactPathOverride(eq("my_output_dir/artifact.jar"));
  }

  @Test
  public void test_customBuildWorkspace() throws AppYamlNotFoundException, IOException {
    String buildScript = "gradle clean test buildThing";
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .file("src/main/appengine/app.yaml")
        .withContents(
            "env: flex\n" +
            "runtime: java\n" +
            "runtime_config:\n" +
            "  build_script: \"" + buildScript + "\"").build()
        .build();

    List<BuildStep> buildSteps = buildPipelineConfigurator.configurePipeline(workspace);
    assertEquals(2, buildSteps.size());
    assertEquals(scriptExecutionBuildStep, buildSteps.get(0));
    assertEquals(stageDockerArtifactBuildStep, buildSteps.get(1));
    verify(buildStepFactory, times(1))
        .createScriptExecutionBuildStep(eq(buildScript));
  }

  @Test(expected = IllegalStateException.class)
  public void test_providedDockerfile() throws AppYamlNotFoundException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("app.yaml").withContents("env: flex\nruntime: java").build()
        .file("Dockerfile").build()
        .build();

    buildPipelineConfigurator.configurePipeline(workspace);
  }

}
