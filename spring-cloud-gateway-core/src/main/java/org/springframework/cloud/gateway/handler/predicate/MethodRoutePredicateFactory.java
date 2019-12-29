/*
 * Copyright 2013-2017 the original author or authors.
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
 */

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;

/**
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: method_route
 *         uri: http://localhost/
 *         predicates:
 *         - Method=GET
 * @author Spencer Gibb
 * 检查这个请求的方式是否符合
 */
public class MethodRoutePredicateFactory extends AbstractRoutePredicateFactory<MethodRoutePredicateFactory.Config> {

	public static final String METHOD_KEY = "method";

	public MethodRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(METHOD_KEY);
	}

	/***
	 *
	 * @param config 泛型参数 config
	 * @return
	 * 1、获取请求方式 GET/PUT/DELETE等
	 * 2、检查请求方式是否满足配置，是的话走该断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return exchange -> {
			HttpMethod requestMethod = exchange.getRequest().getMethod();
			return requestMethod == config.getMethod();
		};
	}

	public static class Config {
		//请求方式 GET/PUT/DELETE等
		private HttpMethod method;

		public HttpMethod getMethod() {
			return method;
		}

		public void setMethod(HttpMethod method) {
			this.method = method;
		}
	}
}
