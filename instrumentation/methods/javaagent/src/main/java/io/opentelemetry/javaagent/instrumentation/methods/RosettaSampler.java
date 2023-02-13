/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This demo sampler filters out all internal spans whose name contains string "greeting".
 *
 * <p>See <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/sdk.md#sampling">
 * OpenTelemetry Specification</a> for more information about span sampling.
 */
public class RosettaSampler implements Sampler {
  private static final AttributeKey<String> HTTP_TARGET_KEY = AttributeKey.stringKey("http.target");

//  private static final Set<String> IGNORE_URI_SET = Sets.newHashSet("/ping", "/admin/sse/connect",
//      "/srv/status/listener/classLessonEnd", "/srv/status/listener/classLessonStart");
@SuppressWarnings("DoubleBraceInitialization")
  private static final Set<String> IGNORE_URI_SET = new HashSet<String>() {{
    add("/ping");  // 健康检测
    add("/sse/connect");  // sse
    add("/listener"); // 长轮询不检测
  }};

  @SuppressWarnings("DoubleBraceInitialization")
  private static final Set<String> DROP_NAME_SET  = new HashSet<String>(){{
    add("KafkaMessageListenerContainer$ListenerConsumer$$Lambda$.run");
    add("org.apache.dubbo.metadata.InstanceMetadataChangedListener/echo");
  }};
      //

  @Override
  public SamplingResult shouldSample(Context parentContext, String traceId, String name,
      SpanKind spanKind, Attributes attributes, List<LinkData> parentLinks) {
//    System.out.println(
//        "RosettaSampler: " + traceId + ",name=" + name + ",spanKind=" + spanKind + ",attributes="
//            + attributes+ ",parentLinks=" + parentLinks+ ",parentContext=" + parentContext);

    if(DROP_NAME_SET.contains(name)){
      return SamplingResult.create(SamplingDecision.DROP);
    }

    // 停止采集所有内部且span名字包含字符串"_st"的span,定时器
    if (spanKind == SpanKind.INTERNAL && name.endsWith("_st")) {
      return SamplingResult.create(SamplingDecision.DROP);
    }

    //redis  操作都不采集
    if (spanKind == SpanKind.CLIENT && name.contains("redis")) {
      return SamplingResult.create(SamplingDecision.DROP);
    }

    // 停止采集所有健康检测路径
    if (spanKind == SpanKind.INTERNAL && name.endsWith("ping")) {
      return SamplingResult.create(SamplingDecision.DROP);
    }
    String httpTarget = attributes.get(HTTP_TARGET_KEY);
    if (httpTarget != null && IGNORE_URI_SET.contains(httpTarget.split("\\?")[0])) {
      return SamplingResult.create(SamplingDecision.DROP);
    }

    return SamplingResult.create(SamplingDecision.RECORD_AND_SAMPLE);
  }

  @Override
  public String getDescription() {
    return "RosettaSampler";
  }
}
