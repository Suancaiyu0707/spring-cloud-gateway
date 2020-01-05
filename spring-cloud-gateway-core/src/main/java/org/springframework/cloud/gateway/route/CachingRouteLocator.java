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
 */

package org.springframework.cloud.gateway.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reactor.cache.CacheFlux;
import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * @author Spencer Gibb
 * 提供了缓存路由的功能
 */
public class CachingRouteLocator implements RouteLocator {

	private final RouteLocator delegate;
	/***
	 *
	 */
	private final Flux<Route> routes;
	/**
	 * 根据key/value 缓存路由映射关系
	 */
	private final Map<String, List> cache = new HashMap<>();

	public CachingRouteLocator(RouteLocator delegate) {
		this.delegate = delegate;
		/***
		 * 0 = {ImmutableSignal@12007} "onNext(Route{id='ce926639-2ed1-45bf-8f8e-7f42b88a9024', uri=http://httpbin.org:80, order=0, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@6a2f36df, gatewayFilters=[OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory$$Lambda$326/373485230@35d86c1e, order=0}]})"
		 * 1 = {ImmutableSignal@12008} "onNext(Route{id='49b879be-f880-43c5-adbd-fe9f73915cb4', uri=http://httpbin.org:80, order=0, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@319a30b2, gatewayFilters=[OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.HystrixGatewayFilterFactory$$Lambda$332/1336210565@57debc35, order=0}]})"
		 * 2 = {ImmutableSignal@12009} "onNext(Route{id='path_route', uri=http://httpbin.org:80, order=0, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@69d6079d, gatewayFilters=[]})"
		 * 3 = {ImmutableSignal@12010} "onNext(Route{id='host_route', uri=http://httpbin.org:80, order=0, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@7a5c5db6, gatewayFilters=[]})"
		 * 4 = {ImmutableSignal@12011} "onNext(Route{id='rewrite_route', uri=http://httpbin.org:80, order=0, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@1a23edef, gatewayFilters=[OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory$$Lambda$340/693083472@38bec58a, order=0}]})"
		 * 5 = {ImmutableSignal@12012} "onNext(Route{id='hystrix_route', uri=http://httpbin.org:80, order=0, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@618dd7ef, gatewayFilters=[OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.HystrixGatewayFilterFactory$$Lambda$332/1336210565@79a48785, order=0}]})"
		 * 6 = {ImmutableSignal@12013} "onNext(Route{id='hystrix_fallback_route', uri=http://httpbin.org:80, order=0, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@7ced41cb, gatewayFilters=[OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.HystrixGatewayFilterFactory$$Lambda$332/1336210565@32315949, order=0}]})"
		 * 7 = {ImmutableSignal@12014} "onNext(Route{id='cookie-route', uri=http://example.org:80, order=0, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@7fd54520, gatewayFilters=[OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory$$Lambda$475/2031941018@dde54e6, order=1}, OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory$$Lambda$326/373485230@4e0a8959, order=1}, OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory$$Lambda$476/1258766299@1cbbace2, order=2}]})"
		 * 8 = {ImmutableSignal@12015} "onNext(Route{id='default_path_to_httpbin', uri=http://example.org:80, order=10000, predicate=org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$323/576902869@2636a4e3, gatewayFilters=[OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory$$Lambda$475/2031941018@6b3be863, order=1}, OrderedGatewayFilter{delegate=org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory$$Lambda$476/1258766299@52e9ef5e, order=2}]})"
		 * 9 = {ImmutableSignal@12016} "onComplete()"
		 */
		routes = CacheFlux.lookup(cache, "routes", Route.class)
				.onCacheMissResume(() -> this.delegate.getRoutes().sort(AnnotationAwareOrderComparator.INSTANCE));
	}

	/***
	 * 返回内部缓存的路由列表
	 * @return
	 */
	@Override
	public Flux<Route> getRoutes() {
		return this.routes;
	}

	/**
	 * 刷新清空缓存内部的路由洗信息
	 * @return routes flux
	 */
	public Flux<Route> refresh() {
		this.cache.clear();
		return this.routes;
	}

	@EventListener(RefreshRoutesEvent.class)
	/* for testing */ void handleRefresh() {
		refresh();
	}
}
