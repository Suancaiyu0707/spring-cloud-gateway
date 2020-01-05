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

import java.util.function.Function;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.DIFFERENT;
import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.DISABLED;
import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.SAME;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * DispatcherHandler接收到请求，匹配 HandlerMapping ，会匹配到 RoutePredicateHandlerMapping 。
 * @author Spencer Gibb
 * 用于处理客户端请求，并查找route
 */
public class RoutePredicateHandlerMapping extends AbstractHandlerMapping {

	private final FilteringWebHandler webHandler;
	/***
	 * 默认是 CachingRouteLocator，立马包含一个组合的delegate属性
	 * 	this.routeLocator = {CachingRouteLocator@6316}
	 *  	delegate = {CompositeRouteLocator@7980}
	 *  	routes = {FluxDefer@7981} "FluxDefer"
	 *  	cache = {HashMap@7982}  size = 0
	 */
	private final RouteLocator routeLocator;
	private final Integer managementPort;
	private final ManagementPortType managementPortType;

	public RoutePredicateHandlerMapping(FilteringWebHandler webHandler,
										RouteLocator routeLocator,
										GlobalCorsProperties globalCorsProperties,
										Environment environment) {
		this.webHandler = webHandler;
		this.routeLocator = routeLocator;

		this.managementPort = getPortProperty(environment, "management.server.");
		this.managementPortType = getManagementPortType(environment);
		//requestMappingHandlerMapping 之后
		setOrder(1);
		setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
	}

	private ManagementPortType getManagementPortType(Environment environment) {
		Integer serverPort = getPortProperty(environment, "server.");
		if (this.managementPort != null && this.managementPort < 0) {
			return DISABLED;
		}
		return ((this.managementPort == null
				|| (serverPort == null && this.managementPort.equals(8080))
				|| (this.managementPort != 0 && this.managementPort.equals(serverPort))) ? SAME
				: DIFFERENT);
	}

	private static Integer getPortProperty(Environment environment, String prefix) {
		return environment.getProperty(prefix + "port", Integer.class);
	}

	/**
	 *
	 * @param exchange：
	 *      	request = {ReactorServerHttpRequest@7859}
	 * 				request = {HttpServerOperations@7250} "GET:/get"
	 * 				bufferFactory = {NettyDataBufferFactory@7871} "NettyDataBufferFactory (PooledByteBufAllocator(directByDefault: true))"
	 * 				uri = {URI@7872} "http://localhost:8070/get"
	 * 				path = {DefaultRequestPath@7873} "DefaultRequestPath[fullPath='[path='/get']', contextPath='', pathWithinApplication='/get']"
	 * 				headers = {HttpHeaders@7874}  size = 11
	 * 				queryParams = null
	 * 				cookies = null
	 * 				sslInfo = null
	 * 			response = {ReactorServerHttpResponse@7860}
	 * 			attributes = {ConcurrentHashMap@7861}  size = 2
	 * 			sessionMono = {MonoProcessor@7862} "MonoProcessor"
	 * 			localeContextResolver = {AcceptHeaderLocaleContextResolver@7863}
	 * 			formDataMono = {MonoProcessor@7864} "MonoProcessor"
	 * 			multipartDataMono = {MonoProcessor@7865} "MonoProcessor"
	 * 			applicationContext = {AnnotationConfigReactiveWebServerApplicationContext@5125} "org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext@2472c7d8: startup date [Sun Dec 29 16:49:30 CST 2019]; parent: org.springframework.context.annotation.AnnotationConfigApplicationContext@1b75c2e3"
	 * 			notModified = false
	 * 			urlTransformer = {DefaultServerWebExchange$lambda@7866}
	 * @return
	 * 1、遍历查找路由列表，并找到匹配的Route路由，并将匹配的路由放到 request属性里，属性名：GATEWAY_ROUTE_ATTR
	 * 2、找到匹配的route后，会返回一个FilteringWebHandler，后续会通过FilteringWebHandler匹配处理
	 * 3、如果没有匹配的路由列表，则返回一个Mono.empty()
	 */
	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
		// don't handle requests on management port if set and different than server port
		if (this.managementPortType == DIFFERENT && this.managementPort != null
				&& exchange.getRequest().getURI().getPort() == this.managementPort) {
			return Mono.empty();
		}
		//设置gatewayHandlerMapper=RoutePredicateHandlerMapping
		exchange.getAttributes().put(GATEWAY_HANDLER_MAPPER_ATTR, getSimpleName());

		return lookupRoute(exchange)//查找匹配Route列表
				// .log("route-predicate-handler-mapping", Level.FINER) //name this
				//返回 Route 的处理器 FilteringWebHandler
				.flatMap((Function<Route, Mono<?>>) r -> {
					//设置 GATEWAY_ROUTE_ATTR 为匹配的route
					exchange.getAttributes().remove(GATEWAY_PREDICATE_ROUTE_ATTR);
					if (logger.isDebugEnabled()) {
						logger.debug("Mapping [" + getExchangeDesc(exchange) + "] to " + r);
					}
					exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, r);
					//返回 FilteringWebHandler
					return Mono.just(webHandler);

				})//匹配不到 Route ，返回 Mono.empty()
				.switchIfEmpty(Mono.empty().then(Mono.fromRunnable(() -> {
					//当前未找到路由时返回空，并移除GATEWAY_PREDICATE_ROUTE_ATTR
					exchange.getAttributes().remove(GATEWAY_PREDICATE_ROUTE_ATTR);
					if (logger.isTraceEnabled()) {
						logger.trace("No RouteDefinition found for [" + getExchangeDesc(exchange) + "]");
					}
				})));
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, ServerWebExchange exchange) {
		// TODO: support cors configuration via properties on a route see gh-229
		// see RequestMappingHandlerMapping.initCorsConfiguration()
		// also see https://github.com/spring-projects/spring-framework/blob/master/spring-web/src/test/java/org/springframework/web/cors/reactive/CorsWebFilterTests.java	        
	    
		return super.getCorsConfiguration(handler, exchange);
	}

	//TODO: get desc from factory?
	private String getExchangeDesc(ServerWebExchange exchange) {
		StringBuilder out = new StringBuilder();
		out.append("Exchange: ");
		out.append(exchange.getRequest().getMethod());
		out.append(" ");
		out.append(exchange.getRequest().getURI());
		return out.toString();
	}

	/***
	 * 根据请求查找路由，exchange里有个request参数，立马可以获得path，如下
	 * @param exchange：
	 * 		request = {ReactorServerHttpRequest@7859}
	 * 				request = {HttpServerOperations@7250} "GET:/get"
	 * 				bufferFactory = {NettyDataBufferFactory@7871} "NettyDataBufferFactory (PooledByteBufAllocator(directByDefault: true))"
	 * 				uri = {URI@7872} "http://localhost:8070/get"
	 * 				path = {DefaultRequestPath@7873} "DefaultRequestPath[fullPath='[path='/get']', contextPath='', pathWithinApplication='/get']"
	 * 				headers = {HttpHeaders@7874}  size = 11
	 * 				queryParams = null
	 * 				cookies = null
	 * 				sslInfo = null
	 * 			response = {ReactorServerHttpResponse@7860}
	 * 			attributes = {ConcurrentHashMap@7861}  size = 2
	 * 			sessionMono = {MonoProcessor@7862} "MonoProcessor"
	 * 			localeContextResolver = {AcceptHeaderLocaleContextResolver@7863}
	 * 			formDataMono = {MonoProcessor@7864} "MonoProcessor"
	 * 			multipartDataMono = {MonoProcessor@7865} "MonoProcessor"
	 * 			applicationContext = {AnnotationConfigReactiveWebServerApplicationContext@5125} "org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext@2472c7d8: startup date [Sun Dec 29 16:49:30 CST 2019]; parent: org.springframework.context.annotation.AnnotationConfigApplicationContext@1b75c2e3"
	 * 			notModified = false
	 * 			urlTransformer = {DefaultServerWebExchange$lambda@7866}
	 * @return
	 * 1、通过路由定位器获取全部路由RouteLocator：CachingRouteLocator、CompositeRouteLocator、RouteDefinitionRouteLocator
	 * 2、通过路由的断言（Predicate）过滤掉不可用的路由信息
	 */
	protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
		//
		return this.routeLocator
				.getRoutes()//获得全部 Route ，。
				//individually filter routes so that filterWhen error delaying is not a problem
				.concatMap(route -> Mono
						.just(route)//顺序匹配一个 Route
						.filterWhen(r -> {//如果找到匹配的Route
							// add the current route we are testing
							exchange.getAttributes().put(GATEWAY_PREDICATE_ROUTE_ATTR, r.getId());
							//返回通过断言过滤的路由信息
							return r.getPredicate().apply(exchange);
						})
						//instead of immediately stopping main flux due to error, log and swallow it
						.doOnError(e -> logger.error("Error applying predicate for route: "+route.getId(), e))
						.onErrorResume(e -> Mono.empty())
				)
				// .defaultIfEmpty() put a static Route not found
				// or .switchIfEmpty()
				// .switchIfEmpty(Mono.<Route>empty().log("noroute"))
				.next()
				//TODO: error handling
				.map(route -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Route matched: " + route.getId());
					}
					validateRoute(route, exchange);
					return route;
				});

		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("RouteDefinition did not match: " + routeDefinition.getId());
			}*/
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * @param route the Route object to validate
	 * @param exchange current exchange
	 * @throws Exception if validation failed
	 */
	@SuppressWarnings("UnusedParameters")
	protected void validateRoute(Route route, ServerWebExchange exchange) {
	}

	protected String getSimpleName() {
		return "RoutePredicateHandlerMapping";
	}

	public enum ManagementPortType {

		/**
		 * The management port has been disabled.
		 */
		DISABLED,

		/**
		 * The management port is the same as the server port.
		 */
		SAME,

		/**
		 * The management port and server port are different.
		 */
		DIFFERENT;
	}
}
