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

package org.springframework.cloud.gateway.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.validation.Validator;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;

/**
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       - id: websocket_test
 *         uri: ws://localhost:9000
 *         order: 9000
 *         predicates:
 *         - Path=/echo
 *         - Query=foo, ba.
 * {@link RouteLocator} that loads routes from a {@link RouteDefinitionLocator}
 * @author Spencer Gibb
 * RouteLocator 最主要的实现类，用于将 RouteDefinition 转换成 Route
 */
public class RouteDefinitionRouteLocator implements RouteLocator, BeanFactoryAware, ApplicationEventPublisherAware {
	protected final Log logger = LogFactory.getLog(getClass());
	/**
	 * 用于提供RouteDefinition
	 */
	private final RouteDefinitionLocator routeDefinitionLocator;
	/***
	 * 会被映射成 key 为 name, value 为 factory 的 Map。可以猜想出 gateway 是如何根据 PredicateDefinition 中定义的 name 来匹配到相对应的 factory 了
	 *	通过它，将 RouteDefinition.predicates 转换成 Route.predicates
	 */
	private final Map<String, RoutePredicateFactory> predicates = new LinkedHashMap<>();
	/**
	 * Gateway Filter 工厂列表，同样会被映射成 key 为 name, value 为 factory 的 Map
	 * 通过它，将 RouteDefinition.filters 转换成 Route.filters
	 */
	private final Map<String, GatewayFilterFactory> gatewayFilterFactories = new HashMap<>();
	/**
	 * 该类依赖 GatewayProperties 对象，后者已经携带了 List 结构的 RouteDefinition
	 * 通过它，将 GatewayProperties.defaultFilters 默认过滤器定义数组，添加到每个 Route
	 */
	private final GatewayProperties gatewayProperties;
	private final SpelExpressionParser parser = new SpelExpressionParser();
	private BeanFactory beanFactory;
	private ApplicationEventPublisher publisher;

	public RouteDefinitionRouteLocator(RouteDefinitionLocator routeDefinitionLocator,//CompositeRouteDefinitionLocator实例
									   //predicates factories，Predicate 工厂列表，会被映射成 key 为 name, value 为 factory 的 Map。
									   List<RoutePredicateFactory> predicates,
									   //Gateway Filter 工厂列表，同样会被映射成 key 为 name, value 为 factory 的 Map
									   List<GatewayFilterFactory> gatewayFilterFactories,
									   //外部化配置类
									   GatewayProperties gatewayProperties) {
		this.routeDefinitionLocator = routeDefinitionLocator;//一个 RouteDefinitionLocator 对象
		//本地内存解析所有的断言处理工厂RoutePredicateFactory，并根据name维护一个映射关系
		initFactories(predicates);
		//遍历所有的 GatewayFilterFactory实现类，并维护到内存里，后续不同的filter会交给不同的GatewayFilterFactory去处理
		gatewayFilterFactories.forEach(factory -> this.gatewayFilterFactories.put(factory.name(), factory));
		this.gatewayProperties = gatewayProperties;
	}

	@Autowired
	private Validator validator;

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/***
	 * 本地内存解析所有的断言处理工厂RoutePredicateFactory，并根据key维护一个映射关系
	 * 后续不同的断言name会交给对应的断言处理工厂处理
	 * @param predicates
	 */
	private void initFactories(List<RoutePredicateFactory> predicates) {
		predicates.forEach(factory -> {
			String key = factory.name();//根据RoutePredicateFactory的实现类名前缀获得工厂名
			if (this.predicates.containsKey(key)) {
				this.logger.warn("A RoutePredicateFactory named "+ key
						+ " already exists, class: " + this.predicates.get(key)
						+ ". It will be overwritten.");
			}
			//根据断言name和断言工厂维护一个映射的map
			this.predicates.put(key, factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded RoutePredicateFactory [" + key + "]");
			}
		});
	}

	/***
	 * 实现 RouteLocator 的 getRoutes() 方法
	 * @return
	 * 1、通过routeDefinitionLocator读取所有的 RouteDefinition
	 * 2、遍历 RouteDefinitions，并调用convertToRoute将 RouteDefinition转换成Route
	 */
	@Override
	public Flux<Route> getRoutes() {
		//1、遍历所有的routeDefinitionLocator.RouteDefinition
		//2、将每个RouteDefinition通过convertToRoute发法转换成router
		return this.routeDefinitionLocator.getRouteDefinitions()
				.map(this::convertToRoute)
				//TODO: error handling
				.map(route -> {
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition matched: " + route.getId());
					}
					return route;
				});


		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("RouteDefinition did not match: " + routeDefinition.getId());
			}*/
	}

	/***
	 * 调用 convertToRoute 方法将 RouteDefinition 转换成 Route。
	 * @param routeDefinition
	 * @return
	 * 1、根据 routeDefinition.predicates 获得 AsyncPredicate
	 * 2、根据 routeDefinition.filters 获得 GatewayFilter
	 * 3、根据 routeDefinition、AsyncPredicate、GatewayFilter生成成Route对象
	 */
	private Route convertToRoute(RouteDefinition routeDefinition) {
		//根据 routeDefinition 获得 AsyncPredicate。
		AsyncPredicate<ServerWebExchange> predicate = combinePredicates(routeDefinition);
		//根据 routeDefinition 获得 GatewayFilter。
		List<GatewayFilter> gatewayFilters = getFilters(routeDefinition);
		//生成 Route 对象。
		return Route.async(routeDefinition)
				.asyncPredicate(predicate)
				.replaceFilters(gatewayFilters)
				.build();
	}

	/**
	 *
	 * @param id routeDefinition的id
	 * @param filterDefinitions
	 * @return
	 * 1、遍历所有的FilterDefinition
	 * 2、根据filter名称都本地内存里获取对应的网关过滤器处理工厂
	 * 3、从FilterDefinition中获得过滤器的配置参数，并根据配置参数初始化一个Config
	 * 4、然后根据Config等参数创建一个GatewayFilter
	 */
	@SuppressWarnings("unchecked")
	private List<GatewayFilter> loadGatewayFilters(String id, List<FilterDefinition> filterDefinitions) {
		List<GatewayFilter> filters = filterDefinitions.stream()
				.map(definition -> {
					GatewayFilterFactory factory = this.gatewayFilterFactories.get(definition.getName());
					if (factory == null) {
                        throw new IllegalArgumentException("Unable to find GatewayFilterFactory with name " + definition.getName());
					}
					Map<String, String> args = definition.getArgs();
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition " + id + " applying filter " + args + " to " + definition.getName());
					}
					//从FilterDefinition中获得过滤器的配置参数，并根据配置参数初始化一个Config
                    Map<String, Object> properties = factory.shortcutType().normalize(args, factory, this.parser, this.beanFactory);

                    Object configuration = factory.newConfig();

                    ConfigurationUtils.bind(configuration, properties,
                            factory.shortcutFieldPrefix(), definition.getName(), validator);
					//根据Config等参数创建一个GatewayFilter
                    GatewayFilter gatewayFilter = factory.apply(configuration);
                    if (this.publisher != null) {
                        this.publisher.publishEvent(new FilterArgsEvent(this, id, properties));
                    }
                    return gatewayFilter;
				})
				.collect(Collectors.toList());

		ArrayList<GatewayFilter> ordered = new ArrayList<>(filters.size());
		for (int i = 0; i < filters.size(); i++) {
			GatewayFilter gatewayFilter = filters.get(i);
			if (gatewayFilter instanceof Ordered) {
				ordered.add(gatewayFilter);
			}
			else {
				ordered.add(new OrderedGatewayFilter(gatewayFilter, i + 1));
			}
		}

		return ordered;
	}

	/***
	 * 根据 RouteDefinition 获取过滤器列表
	 * @param routeDefinition
	 * @return
	 * 1、处理 GatewayProperties 中定义的默认的 FilterDefinition，转换成 GatewayFilter。
	 * 2、将 RouteDefinition 中定义的 FilterDefinition 转换成 GatewayFilter。
	 * 3、对 GatewayFilter 进行排序，排序的详细逻辑请查阅 spring 中的 Ordered 接口。
	 */
	private List<GatewayFilter> getFilters(RouteDefinition routeDefinition) {
		List<GatewayFilter> filters = new ArrayList<>();

		//处理 GatewayProperties 中定义的默认的 FilterDefinition，转换成 GatewayFilter。
		if (!this.gatewayProperties.getDefaultFilters().isEmpty()) {
			filters.addAll(loadGatewayFilters("defaultFilters",
					this.gatewayProperties.getDefaultFilters()));
		}
		//将 RouteDefinition 中定义的 FilterDefinition 转换成 GatewayFilter。
		if (!routeDefinition.getFilters().isEmpty()) {
			filters.addAll(loadGatewayFilters(routeDefinition.getId(), routeDefinition.getFilters()));
		}
		//对 GatewayFilter 进行排序，排序的详细逻辑请查阅 spring 中的 Ordered 接口。
		AnnotationAwareOrderComparator.sort(filters);
		return filters;
	}

	/***
	 * 根据RouteDefinition的断言列表组合生成 AsyncPredicate
	 * @param routeDefinition
	 * @return
	 * 1、获取RouteDefinition订阅的断言列表信息：routeDefinition.predicates
	 * 2、将列表中第一个 PredicateDefinition 转换成 AsyncPredicate
	 * 3、循环routeDefinition.predicates列表，将剩下的其它的断言组合到AsyncPredicate对象里
	 */
	private AsyncPredicate<ServerWebExchange> combinePredicates(RouteDefinition routeDefinition) {
		//从RouteDefinition获得断言列表信息
		List<PredicateDefinition> predicates = routeDefinition.getPredicates();
		//将列表中第一个 PredicateDefinition 转换成 AsyncPredicate
		AsyncPredicate<ServerWebExchange> predicate = lookup(routeDefinition, predicates.get(0));
		//循环调用，将列表中每一个 PredicateDefinition 都转换成 AsyncPredicate
		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			AsyncPredicate<ServerWebExchange> found = lookup(routeDefinition, andPredicate);
			//应用and操作，将所有的 AsyncPredicate 组合成一个 组合的AsyncPredicate 对象
			predicate = predicate.and(found);
		}

		return predicate;
	}

	/***
	 * 将 predicate 转换成一个 AsyncPredicate
	 * @param route 路由信息
	 * @param predicate
	 * @return
	 * 1、根据断言的name从本地内存里predicates获得相应的断言处理工厂RoutePredicateFactory
	 * 2、获取RouteDefinition.predicate的断言配置参数，并进行处理
	 * 3、根据实际的断言工厂实现，生成一个断言配置config，然后将断言参数绑定到config
	 * 4、根据config返回一个AsyncPredicate对象
	 */
	@SuppressWarnings("unchecked")
	private AsyncPredicate<ServerWebExchange> lookup(RouteDefinition route, PredicateDefinition predicate) {
		//根据 predicate 名称获取对应的 predicate factory
		RoutePredicateFactory<Object> factory = this.predicates.get(predicate.getName());
		if (factory == null) {
            throw new IllegalArgumentException("Unable to find RoutePredicateFactory with name " + predicate.getName());
		}
		//获得断言参数
		Map<String, String> args = predicate.getArgs();
		if (logger.isDebugEnabled()) {
			logger.debug("RouteDefinition " + route.getId() + " applying "
					+ args + " to " + predicate.getName());
		}
		//对参数作进一步转换，key为 config 类（工厂类中通过泛型指定）的属性名称。
        Map<String, Object> properties = factory.shortcutType().normalize(args, factory, this.parser, this.beanFactory);
		//调用 factory 的 newConfig 方法创建一个 config 类对象
        Object config = factory.newConfig();
		//将参数绑定到 config 对象上。
        ConfigurationUtils.bind(config, properties,
                factory.shortcutFieldPrefix(), predicate.getName(), validator);
        if (this.publisher != null) {
            this.publisher.publishEvent(new PredicateArgsEvent(this, route.getId(), properties));
        }
        //将 cofing 作参数代入，调用 factory 的 applyAsync 方法创建 AsyncPredicate 对象。
        return factory.applyAsync(config);
	}
}
