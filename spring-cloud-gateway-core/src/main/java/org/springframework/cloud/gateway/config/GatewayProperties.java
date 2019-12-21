
/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 *
spring:
  cloud:
    gateway:
      routes:
      - id: cookie-route
        uri: http://example.org
        predicates:
        - Cookie=username, xuzf #定义了一个 Predicate，当名称为 username 的 Cookie 的值匹配xuzf时 Predicate 才能够匹配，它由 CookieRoutePredicateFactory 来生产
        filters:
        - AddRequestHeader=X-Request-Foo, Bar # 定义了一个 Filter，所有的请求转发至下游服务时会添加请求头 X-Request-Foo:Bar ，由AddRequestHeaderGatewayFilterFactory 来生产。
      - id: default_path_to_httpbin
        uri: http://example.org
        order: 10000
        predicates:
        - Path=/**
      default-filters:
      - PrefixPath=/httpbin
      - AddResponseHeader=X-Response-Default-Foo, Default-Bar
 */

package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

/**
 * @author Spencer Gibb
 * GatewayProperties 是 Spring cloud gateway 模块提供的外部化配置类。
 *  从appliccation.yml中解析前缀为spring.cloud.gateway的配置
 */
@ConfigurationProperties("spring.cloud.gateway")//表明以 “spring.cloud.gateway” 前缀的 properties 会绑定 GatewayProperties。
@Validated
public class GatewayProperties {

	private final Log logger = LogFactory.getLog(getClass());
	/**
	 * 路由定义列表，加载配置key=spring.cloud.gateway.routes 列表
	 * List of Routes
	 */
	@NotNull
	@Valid
	private List<RouteDefinition> routes = new ArrayList<>();

	/**
	 * 默认的过滤器定义列表，加载配置 key = spring.cloud.gateway.default-filters 列表
	 * List of filter definitions that are applied to every route.
	 *
	 * 默认的 Filter 会应用到每一个 Route 上，gateway 处理时会将其与 Route 中指定的 Filter 进行合并后并逐个执行。
	 */
	private List<FilterDefinition> defaultFilters = new ArrayList<>();
	/**
	 * 网媒体类型列表，加载配置 key = spring.cloud.gateway.streamingMediaTypes 列表
	 * 默认包含{text/event-stream,application/stream+json}
	 */
	private List<MediaType> streamingMediaTypes = Arrays.asList(MediaType.TEXT_EVENT_STREAM,
			MediaType.APPLICATION_STREAM_JSON);

	public List<RouteDefinition> getRoutes() {
		return routes;
	}


	public void setRoutes(List<RouteDefinition> routes) {
		this.routes = routes;
		if (routes != null && routes.size() > 0 && logger.isDebugEnabled()) {
			logger.debug("Routes supplied from Gateway Properties: "+routes);
		}
	}

	public List<FilterDefinition> getDefaultFilters() {
		return defaultFilters;
	}

	public void setDefaultFilters(List<FilterDefinition> defaultFilters) {
		this.defaultFilters = defaultFilters;
	}

	public List<MediaType> getStreamingMediaTypes() {
		return streamingMediaTypes;
	}

	public void setStreamingMediaTypes(List<MediaType> streamingMediaTypes) {
		this.streamingMediaTypes = streamingMediaTypes;
	}

	@Override
	public String toString() {
		return "GatewayProperties{" +
				"routes=" + routes +
				", defaultFilters=" + defaultFilters +
				", streamingMediaTypes=" + streamingMediaTypes +
				'}';
	}
}
