/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractClientIpFromForwardedForHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractClientIpFromForwardedHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractProtoFromForwardedHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractProtoFromForwardedProtoHeader;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InternalNetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-server">HTTP
 * server attributes</a>. Instrumentation of HTTP server frameworks should extend this class,
 * defining {@link REQUEST} and {@link RESPONSE} with the actual request / response types of the
 * instrumented library. If an attribute is not available in this library, it is appropriate to
 * return {@code null} from the protected attribute methods, but implement as many as possible for
 * best compliance with the OpenTelemetry specification.
 */
public final class HttpServerAttributesExtractor<REQUEST, RESPONSE>
    extends HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, HttpServerAttributesGetter<REQUEST, RESPONSE>>
    implements SpanKeyProvider {

  /**
   * Creates the HTTP server attributes extractor with default configuration.
   *
   * @deprecated Use {@link #create(HttpServerAttributesGetter, NetServerAttributesGetter)} instead.
   */
  @Deprecated
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    return create(httpAttributesGetter, new NoopNetServerAttributesGetter<>());
  }

  /** Creates the HTTP server attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetServerAttributesGetter<REQUEST> netAttributesGetter) {
    return builder(httpAttributesGetter, netAttributesGetter).build();
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractorBuilder} that can be used to configure the
   * HTTP client attributes extractor.
   *
   * @deprecated Use {@link #builder(HttpServerAttributesGetter, NetServerAttributesGetter)}
   *     instead.
   */
  @Deprecated
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    return builder(httpAttributesGetter, new NoopNetServerAttributesGetter<>());
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractorBuilder} that can be used to configure the
   * HTTP client attributes extractor.
   */
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetServerAttributesGetter<REQUEST> netAttributesGetter) {
    return new HttpServerAttributesExtractorBuilder<>(httpAttributesGetter, netAttributesGetter);
  }

  private final NetServerAttributesGetter<REQUEST> netAttributesGetter;
  private final Function<Context, String> httpRouteHolderGetter;

  HttpServerAttributesExtractor(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetServerAttributesGetter<REQUEST> netAttributesGetter,
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders) {
    this(
        httpAttributesGetter,
        netAttributesGetter,
        capturedRequestHeaders,
        capturedResponseHeaders,
        HttpRouteHolder::getRoute);
  }

  // visible for tests
  HttpServerAttributesExtractor(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetServerAttributesGetter<REQUEST> netAttributesGetter,
      List<String> capturedRequestHeaders,
      List<String> responseHeaders,
      Function<Context, String> httpRouteHolderGetter) {
    super(httpAttributesGetter, capturedRequestHeaders, responseHeaders);
    this.netAttributesGetter = netAttributesGetter;
    this.httpRouteHolderGetter = httpRouteHolderGetter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);

    internalSet(attributes, SemanticAttributes.HTTP_FLAVOR, getter.flavor(request));
    String forwardedProto = forwardedProto(request);
    String value = forwardedProto != null ? forwardedProto : getter.scheme(request);
    internalSet(attributes, SemanticAttributes.HTTP_SCHEME, value);
    internalSet(attributes, SemanticAttributes.HTTP_TARGET, getter.target(request));
    internalSet(attributes, SemanticAttributes.HTTP_ROUTE, getter.route(request));
    internalSet(attributes, SemanticAttributes.HTTP_CLIENT_IP, clientIp(request));
    setUserOaUid(attributes,request);
    setReqId(attributes,request);

    InternalNetServerAttributesExtractor.onStart(
        netAttributesGetter, attributes, request, host(request));
  }


  public static final AttributeKey<String> OA_UID = AttributeKey.stringKey("http.oa_uid");
  public static final AttributeKey<String> R_UID = AttributeKey.stringKey("http.uid");
  public static final AttributeKey<String> X_REQID = AttributeKey.stringKey("http.x_request_id");

  private void setReqId(AttributesBuilder attributes, REQUEST request) {
    String reqId = firstHeaderValue(getter.requestHeader(request, "x-request-id"));
    if (reqId == null || reqId.length() < 1) {
      return;
    }
    internalSet(attributes, X_REQID, reqId);
  }

  private void setUserOaUid(AttributesBuilder attributes, REQUEST request) {
    String cookie = firstHeaderValue(getter.requestHeader(request, "cookie"));
    if (cookie == null || cookie.length() < 1) {
      return;
    }
    String[] tokens = cookie.split("; ");

    int findNum = 0;
    for (String token : tokens) {
      int i = token.indexOf('=');
      if (i > 0 && i < token.length()) {
        String name = token.substring(0, i).trim();
        if ("uid".equals(name)) {
          internalSet(attributes, OA_UID, token.substring(i + 1).trim());
          findNum = findNum + 1;
        } else if ("r_udb_uid".equals(name)) {
          internalSet(attributes, R_UID, token.substring(i + 1).trim());
          findNum = findNum + 1;
        }
      }
      if (findNum == 2) {
        return;
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    super.onEnd(attributes, context, request, response, error);
    internalSet(attributes, SemanticAttributes.HTTP_ROUTE, httpRouteHolderGetter.apply(context));
  }

  @Nullable
  private String host(REQUEST request) {
    return firstHeaderValue(getter.requestHeader(request, "host"));
  }

  @Nullable
  private String forwardedProto(REQUEST request) {
    // try Forwarded
    String forwarded = firstHeaderValue(getter.requestHeader(request, "forwarded"));
    if (forwarded != null) {
      forwarded = extractProtoFromForwardedHeader(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    // try X-Forwarded-Proto
    forwarded = firstHeaderValue(getter.requestHeader(request, "x-forwarded-proto"));
    if (forwarded != null) {
      return extractProtoFromForwardedProtoHeader(forwarded);
    }

    return null;
  }

  @Nullable
  private String clientIp(REQUEST request) {
    // try Forwarded
    String forwarded = firstHeaderValue(getter.requestHeader(request, "forwarded"));
    if (forwarded != null) {
      forwarded = extractClientIpFromForwardedHeader(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    // try X-Forwarded-For
    forwarded = firstHeaderValue(getter.requestHeader(request, "x-forwarded-for"));
    if (forwarded != null) {
      return extractClientIpFromForwardedForHeader(forwarded);
    }

    return null;
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.HTTP_SERVER;
  }

  private static class NoopNetServerAttributesGetter<REQUEST>
      implements NetServerAttributesGetter<REQUEST> {

    @Nullable
    @Override
    public String transport(REQUEST request) {
      return null;
    }

    @Nullable
    @Override
    public String hostName(REQUEST request) {
      return null;
    }

    @Nullable
    @Override
    public Integer hostPort(REQUEST request) {
      return null;
    }
  }
}
