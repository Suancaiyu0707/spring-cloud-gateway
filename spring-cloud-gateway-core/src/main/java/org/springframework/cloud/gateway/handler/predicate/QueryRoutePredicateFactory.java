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

import javax.validation.constraints.NotEmpty;

import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: query_route
 *         uri: http://example.org
 *         predicates:
 *         - Query=name  #表示param是name即可
 *         - Query=name, xuzf # 表示param是name，regexp是xuzf
 * 根据request中的QueryParam请求参数匹配
 * @author Spencer Gibb
 */
public class QueryRoutePredicateFactory extends AbstractRoutePredicateFactory<QueryRoutePredicateFactory.Config> {

	public static final String PARAM_KEY = "param";
	public static final String REGEXP_KEY = "regexp";

	public QueryRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(PARAM_KEY, REGEXP_KEY);
	}

	/**
	 *
	 * @param config 泛型参数 config
	 * @return
	 * 1、如果regexp为空，也就是不是按照正则来匹配的话。则直接判断请求参数里是否包含指定的参数param
	 * 2、如果regexp不为空，也就是根据正则来匹配参数的值
	 * 		a、如果请求中没有传递参数，则不走该断言
	 * 		b、如果传递参数中，包含指定的参数，则可能传递多个值，则只要有一个值匹配正则，则走该断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return exchange -> {
			//
			if (!StringUtils.hasText(config.regexp)) {
				// check existence of header
				return exchange.getRequest().getQueryParams().containsKey(config.param);
			}
			//如果regexp不为空，也就是根据正则来匹配参数的值
			List<String> values = exchange.getRequest().getQueryParams().get(config.param);
			if (values == null) {//如果请求中没有传递参数，则不走该断言
				return false;
			}
			//如果传递参数中，包含指定的参数，则可能传递多个值，则只要有一个值匹配正则，则走该断言
			for (String value : values) {
				if (value != null && value.matches(config.regexp)) {
					return true;
				}
			}
			return false;
		};
	}

	@Validated
	public static class Config {
		@NotEmpty
		private String param;
		/**
		 * 如果是按照正则匹配的话
		 */
		private String regexp;

		public String getParam() {
			return param;
		}

		public Config setParam(String param) {
			this.param = param;
			return this;
		}

		public String getRegexp() {
			return regexp;
		}

		public Config setRegexp(String regexp) {
			this.regexp = regexp;
			return this;
		}
	}
}
