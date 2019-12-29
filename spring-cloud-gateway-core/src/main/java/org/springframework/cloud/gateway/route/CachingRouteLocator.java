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
	private final Flux<Route> routes;
	/**
	 * 根据key/value 缓存路由映射关系
	 */
	private final Map<String, List> cache = new HashMap<>();

	public CachingRouteLocator(RouteLocator delegate) {
		this.delegate = delegate;
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
