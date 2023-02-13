/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSamplerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

@AutoService(ConfigurableSamplerProvider.class)
public class RosettaConfigurableSamplerProvider implements ConfigurableSamplerProvider {

  @Override
  public Sampler createSampler(ConfigProperties config) {
    return new RosettaSampler();
  }

  @Override
  public String getName() {
    return "rosetta_sampler_provider";
  }
}
