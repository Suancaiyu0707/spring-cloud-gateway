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

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 * 对象会根据匹配的Route，计算请求的地址。注意，这里的地址指的是 URL，而不是 URI
 */
public class RouteToRequestUrlFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(RouteToRequestUrlFilter.class);

	public static final int ROUTE_TO_URL_FILTER_ORDER = 10000;
	private static final String SCHEME_REGEX = "[a-zA-Z]([a-zA-Z]|\\d|\\+|\\.|-)*:.*";
	static final Pattern schemePattern = Pattern.compile(SCHEME_REGEX);

	@Override
	public int getOrder() {
		return ROUTE_TO_URL_FILTER_ORDER;
	}

	/***
	 *
	 * @param exchange the current server exchange
	 * @param chain provides a way to delegate to the next filter
	 * @return
	 * 1、根据gatewayRoute获取Route
	 * 2、获取requets的uri
	 * 3、获取路由指向的目的地uri,也就是客户端请求最终被转发的地址
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		//根据gatewayRoute获取Route
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		if (route == null) {
			return chain.filter(exchange);
		}
		log.trace("RouteToRequestUrlFilter start");
		//获取requets的uri
		URI uri = exchange.getRequest().getURI();
		boolean encoded = containsEncodedParts(uri);
		//获取路由指向的目的地uri,也就是客户端请求最终被转发的地址
		URI routeUri = route.getUri();
		//检查routeUri的合法性
		if (hasAnotherScheme(routeUri)) {
			// this is a special url, save scheme to special attribute
			// replace routeUri with schemeSpecificPart
			exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, routeUri.getScheme());
			routeUri = URI.create(routeUri.getSchemeSpecificPart());
		}

		if("lb".equalsIgnoreCase(routeUri.getScheme()) && routeUri.getHost() == null) {
			//Load balanced URIs should always have a host.  If the host is null it is most
			//likely because the host name was invalid (for example included an underscore)
			throw new IllegalStateException("Invalid host: " + routeUri.toString());
		}
		// 拼接 requestUrl
		URI mergedUrl = UriComponentsBuilder.fromUri(uri)
				// .uri(routeUri)
				.scheme(routeUri.getScheme()) // schema
				.host(routeUri.getHost()) //host
				.port(routeUri.getPort())//port
				.build(encoded)
				.toUri();
		// 设置 requestUrl 到 GATEWAY_REQUEST_URL_ATTR {@link RewritePathGatewayFilterFactory}
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, mergedUrl);
		// 提交过滤器链继续过滤
		return chain.filter(exchange);
	}

	/* for testing */ static boolean hasAnotherScheme(URI uri) {
		return schemePattern.matcher(uri.getSchemeSpecificPart()).matches()
				&& uri.getHost() == null
				&& uri.getRawPath() == null;
	}
}
