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

package com.google.cloud.runtimes.builder.injection;

import com.google.cloud.runtimes.builder.buildsteps.base.BuildStepFactory;
import com.google.cloud.runtimes.builder.buildsteps.docker.DefaultDockerfileGenerator;
import com.google.cloud.runtimes.builder.buildsteps.docker.DockerfileGenerator;
import com.google.cloud.runtimes.builder.config.AppYamlFinder;
import com.google.cloud.runtimes.builder.config.AppYamlParser;
import com.google.cloud.runtimes.builder.config.YamlParser;
import com.google.cloud.runtimes.builder.config.domain.AppYaml;
import com.google.cloud.runtimes.builder.config.domain.JdkServerLookup;
import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Module class for configuring Guice bindings.
 */
public class RootModule extends AbstractModule {

  private final String[] jdkMappings;
  private final String[] serverMappings;
  private static final String CONFIG_YAML_ENV_VAR = "GAE_APPLICATION_YAML_PATH";

  /**
   * Constructs a new {@link RootModule} for Guice.
   *
   * @param jdkMappings mappings between supported jdk versions and docker images
   * @param serverMappings mappings between supported jdk versions, server types, and docker images
   */
  public RootModule(String[] jdkMappings, String[] serverMappings) {
    Preconditions.checkNotNull(jdkMappings);
    Preconditions.checkNotNull(serverMappings);

    this.jdkMappings = jdkMappings;
    this.serverMappings = serverMappings;
  }

  @Override
  protected void configure() {
    bind(new TypeLiteral<Optional<String>>(){})
        .annotatedWith(ConfigYamlPath.class)
        .toInstance(Optional.ofNullable(System.getenv(CONFIG_YAML_ENV_VAR)));

    bind(new TypeLiteral<YamlParser<AppYaml>>(){})
        .to(AppYamlParser.class);
    bind(DockerfileGenerator.class)
        .to(DefaultDockerfileGenerator.class);
    bind(AppYamlFinder.class);

    install(new FactoryModuleBuilder()
        .build(BuildStepFactory.class));
  }

  @Provides
  protected JdkServerLookup provideJdkServerLookup() throws IOException {
    return new JdkServerLookup(buildMap(jdkMappings), buildMap(serverMappings));
  }

  /*
   * Converts an array of mapping strings, with keys and values separated by an '=' character, into
   * a Map<String,String>.
   */
  private static Map<String, String> buildMap(String[] mappings) {
    return Arrays.stream(mappings)
        .map(s -> {
          String[] split = s.split("=");
          // make sure mappings are formatted correctly
          if (split.length != 2)  {
            throw new IllegalArgumentException("Invalid mapping: '" + s + "'. "
                + "All jdk/server mappings must be formatted as: KEY=VAL");
          }
          return split;
        })
        .collect(Collectors.toMap(a -> a[0], a -> a[1]));
  }

}
