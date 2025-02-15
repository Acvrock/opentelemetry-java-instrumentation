/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

enum SpringWebHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, ClientHttpResponse> {
  INSTANCE;

  @Override
  public String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  @Nullable
  public String url(HttpRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  public List<String> requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeaders().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  public String flavor(HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Nullable private static final MethodHandle GET_STATUS_CODE;

  @Nullable private static final MethodHandle STATUS_CODE_VALUE;

  static {
    MethodHandle getStatusCode = null;
    MethodHandle statusCodeValue = null;
    Class<?> httpStatusCodeClass = null;

    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    try {
      httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
    } catch (ClassNotFoundException e) {
      try {
        httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatus");
      } catch (ClassNotFoundException ignored) {
        // ignored
      }
    }

    if (httpStatusCodeClass != null) {
      try {
        getStatusCode =
            lookup.findVirtual(
                ClientHttpResponse.class,
                "getStatusCode",
                MethodType.methodType(httpStatusCodeClass));
        statusCodeValue =
            lookup.findVirtual(httpStatusCodeClass, "value", MethodType.methodType(int.class));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
        // ignored
      }
    }

    GET_STATUS_CODE = getStatusCode;
    STATUS_CODE_VALUE = statusCodeValue;
  }

  @Override
  public Integer statusCode(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, @Nullable Throwable error) {

    if (GET_STATUS_CODE == null || STATUS_CODE_VALUE == null) {
      return null;
    }

    try {
      Object statusCode = GET_STATUS_CODE.invoke(clientHttpResponse);
      return (int) STATUS_CODE_VALUE.invoke(statusCode);
    } catch (Throwable e) {
      return null;
    }
  }

  @Override
  public List<String> responseHeader(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, String name) {
    return clientHttpResponse.getHeaders().getOrDefault(name, emptyList());
  }
}
