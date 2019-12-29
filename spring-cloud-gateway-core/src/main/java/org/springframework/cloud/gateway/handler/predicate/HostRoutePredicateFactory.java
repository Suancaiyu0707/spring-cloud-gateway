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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: host_route
 *         uri: http://example.org
 *         predicates:
 *         - Host=**.somehost.org
 * @author Spencer Gibb
 * 匹配请求中的request header中的Host的值
 */
public class HostRoutePredicateFactory extends AbstractRoutePredicateFactory<HostRoutePredicateFactory.Config> {
	/***
	 * 路径匹配器
	 */
	private PathMatcher pathMatcher = new AntPathMatcher(".");

	public HostRoutePredicateFactory() {
		super(Config.class);
	}

	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList(PATTERN_KEY);
	}

	/**
	 *
	 * @param config 泛型参数 config
	 * @return
	 * 1、获得请求中的request header中的Host的值，如果有多个的话，获取第一个即可
	 * 2、检查Host的值是否满足正则，是的话，走该断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return exchange -> {
			String host = exchange.getRequest().getHeaders().getFirst("Host");
			boolean match = this.pathMatcher.match(config.getPattern(), host);
			if (match) {
				Map<String, String> variables = this.pathMatcher.extractUriTemplateVariables(config.getPattern(), host);
				ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);
			}
			return match;
		};
	}

	@Validated
	public static class Config {
		/**
		 * Host的匹配模式
		 */
		private String pattern;

		public String getPattern() {
			return pattern;
		}

		public Config setPattern(String pattern) {
			this.pattern = pattern;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("pattern", pattern)
					.toString();
		}
	}
}
