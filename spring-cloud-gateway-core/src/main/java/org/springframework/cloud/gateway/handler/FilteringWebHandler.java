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

package org.springframework.cloud.gateway.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * WebHandler that delegates to a chain of {@link GlobalFilter} instances and
 * {@link GatewayFilterFactory} instances then to the target {@link WebHandler}.
 *
 * @author Rossen Stoyanchev
 * @author Spencer Gibb
 * @since 0.1
 *
 *	FilteringWebHandler 通过创建请求对应的 Route 对应的 GatewayFilterChain 进行处理
 */
public class FilteringWebHandler implements WebHandler {
	protected static final Log logger = LogFactory.getLog(FilteringWebHandler.class);
	/**
	 *
	 * 全局的过滤器
	 * 0 = {AdaptCachedBodyGlobalFilter@6128}
	 * 1 = {NettyWriteResponseFilter@6188}
	 * 2 = {ForwardPathFilter@6195}
	 * 3 = {RouteToRequestUrlFilter@6200}
	 * 4 = {WebsocketRoutingFilter@6204}
	 * 5 = {NettyRoutingFilter@6208}
	 * 6 = {ForwardRoutingFilter@6212}
	 */
	private final List<GatewayFilter> globalFilters;

	public FilteringWebHandler(List<GlobalFilter> globalFilters) {
		this.globalFilters = loadFilters(globalFilters);
	}

	/**
	 * 包装加载全局的过滤器，将全局过滤器包装成GatewayFilter：将每个 GlobalFilter 会被适配成GatewayFilterAdapter，如果GlobalFilter是一个有序的Filter，则转换成OrderedGatewayFilter
	 * @param filters
	 * @return
	 * 1、遍历GlobalFilter，将GlobalFilter用适配器适配成一个实现了GatewayFilter接口的GatewayFilterAdapter。
	 * 2、如果 GlobalFilter本身是一个有序的Ordered，则再将GatewayFilterAdapter包装成一个有序的OrderedGatewayFilter。
	 * 3、返回包装的GatewayFilter列表
	 */
	private static List<GatewayFilter> loadFilters(List<GlobalFilter> filters) {
		return filters.stream()
				.map(filter -> {
					//我们发现在这里，GlobalFilter 会被适配成GatewayFilterAdapter
					GatewayFilterAdapter gatewayFilter = new GatewayFilterAdapter(filter);
					//如果GlobalFilter是一个有序的Filter，则转换成OrderedGatewayFilter
					if (filter instanceof Ordered) {
						int order = ((Ordered) filter).getOrder();
						return new OrderedGatewayFilter(gatewayFilter, order);
					}
					return gatewayFilter;
				}).collect(Collectors.toList());
	}

    /* TODO: relocate @EventListener(RefreshRoutesEvent.class)
    void handleRefresh() {
        this.combinedFiltersForRoute.clear();
    }*/

	/***
	 * 执行当前路由的过滤器和全局过滤器
	 * @param exchange
	 * @return
	 * 1、获得处理的 Route,这个Route是在RoutePredicateHandlerMapping类中put进来
	 * 2、获得该route下配置的网关过滤器列表
	 * 3、把全局过滤器列表和route独有的过滤器列表都加入combined
	 * 4、对合并后的combined列表进行排序
	 * 5、根据combined创建过滤器链，并开始进行链式调用
	 */
	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		//获得 Route,这个Route是在RoutePredicateHandlerMapping类中put进来
		Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
		//获得该route下的网关过滤器列表
		List<GatewayFilter> gatewayFilters = route.getFilters();
		//把全局过滤链和route独有的过滤链都加入combined
		List<GatewayFilter> combined = new ArrayList<>(this.globalFilters);
		combined.addAll(gatewayFilters);
		//TODO: needed or cached?
		AnnotationAwareOrderComparator.sort(combined);

		if (logger.isDebugEnabled()) {
			logger.debug("Sorted gatewayFilterFactories: "+ combined);
		}
		//根据combined创建过滤器链，并开始进行链式调用
		return new DefaultGatewayFilterChain(combined).filter(exchange);
	}

	private static class DefaultGatewayFilterChain implements GatewayFilterChain {

		private final int index;
		/**
		 * 过滤器链上绑定的过滤器列表
		 */
		private final List<GatewayFilter> filters;

		public DefaultGatewayFilterChain(List<GatewayFilter> filters) {
			this.filters = filters;
			this.index = 0;
		}

		private DefaultGatewayFilterChain(DefaultGatewayFilterChain parent, int index) {
			this.filters = parent.getFilters();
			this.index = index;
		}

		public List<GatewayFilter> getFilters() {
			return filters;
		}

		/***
		 * 遍历过滤器链上的每一个过滤器，对exchange中的request进行过滤
		 * @param exchange the current server exchange
		 * @return
		 */
		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			//遍历过滤器链上的每一个过滤器，对exchange中的request进行过滤
			return Mono.defer(() -> {
				if (this.index < filters.size()) {
					//根据索引获得指定的过滤器
					GatewayFilter filter = filters.get(this.index);
					//记录当前执行的过滤器的索引，这样保证沿着过滤器链传递下去
					DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(this, this.index + 1);
					return filter.filter(exchange, chain);
				} else {
					return Mono.empty(); // complete
				}
			});
		}
	}

	/***
	 * 是一个 GatewayFilter的适配器
	 * 在 GatewayFilterChain 使用 GatewayFilter 过滤请求，所以通过 GatewayFilterAdapter 将 GlobalFilter 适配成 GatewayFilter
	 */
	private static class GatewayFilterAdapter implements GatewayFilter {
		/***
		 * 被适配的过滤器，是一个GlobalFilter，然后会被适配成一个GatewayFilter
		 */
		private final GlobalFilter delegate;

		public GatewayFilterAdapter(GlobalFilter delegate) {
			this.delegate = delegate;
		}

		/***
		 * 传递给被适配的过滤器过滤
		 * @param exchange the current server exchange
		 * @param chain provides a way to delegate to the next filter
		 * @return
		 */
		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return this.delegate.filter(exchange, chain);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("GatewayFilterAdapter{");
			sb.append("delegate=").append(delegate);
			sb.append('}');
			return sb.toString();
		}
	}

}
