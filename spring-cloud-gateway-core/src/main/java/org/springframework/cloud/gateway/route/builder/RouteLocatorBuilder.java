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
 */

package org.springframework.cloud.gateway.route.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ConfigurableApplicationContext;

import reactor.core.publisher.Flux;

/**
 * Used to build a {@link RouteLocator}
 * RouteLocatorBuilder bean 在 spring-cloud-starter-gateway 模块自动装配类中已经声明，可直接使用。
 * RouteLocatorBuilder用于创建RouteLocator，RouteLocator 封装了对 Route 获取的定义，可简单理解成工厂模式。
 * RouteLocatorBuilder 可以构建多个路由信息
 */
public class RouteLocatorBuilder {

	private ConfigurableApplicationContext context;

	public RouteLocatorBuilder(ConfigurableApplicationContext context) {
		this.context = context;
	}

	/**
	 * 创建一个RouteLocatorBuilder，并绑定spring上下文
	 */
	public Builder routes() {
		return new Builder(context);
	}

	/**
	 * A class that can be used to construct routes and return a {@link RouteLocator}
	 */
	public static class Builder {
		/***
		 * LocatorBuilder 已创建好的 Route 数组
		 */
		private List<Route.AsyncBuilder> routes = new ArrayList<>();
		private ConfigurableApplicationContext context;

		public Builder(ConfigurableApplicationContext context) {
			this.context = context;
		}

		/**
		 * Creates a new {@link Route}
		 * @param id the unique id for the route
		 * @param fn a function which takes in a {@link PredicateSpec} and returns a {@link Route.AsyncBuilder}
		 * @return a {@link Builder}
		 * 1、创建RouteSpec对象
		 * 2、调用 RouteSpec#id(...) 方法，创建 PredicateSpec 对象
		 */
		/***
		 * 创建一个新的路由对象：{@link Route}
		 * @param id  指定路由Id
		 * @param fn 用于创建对应的路由构建器Route.AsyncBuilder的函数，创建的Route.AsyncBuilder会绑定对应的断言信息
		 * @return
		 * 1、根据当前的RouteLocatorBuilder创建一个RouteSpec
		 * 2、RouteSpec会根据断言配置构建一个断言信息创建一个PredicateSpec。
		 * 3、fn会根据PredicateSpec创建一个routeBuilder（本质上是PredicateSpec会根据fn的断言配置调用相应的方法）
		 */
		public Builder route(String id, Function<PredicateSpec, Route.AsyncBuilder> fn) {
			Route.AsyncBuilder routeBuilder = fn.apply(
					new RouteSpec(this)//根据当前Router构建器创建Route标准对象：RouteSpec对象，这个
					.id(id)//调用 RouteSpec#id(...) 方法，创建 PredicateSpec 对象
			);
			add(routeBuilder);
			return this;
		}

		/**
		 * Creates a new {@link Route}
		 * @param fn a function which takes in a {@link PredicateSpec} and returns a {@link Route.AsyncBuilder}
		 * @return a {@link Builder}

		 */
		public Builder route(Function<PredicateSpec, Route.AsyncBuilder> fn) {
			Route.AsyncBuilder routeBuilder = fn.apply(new RouteSpec(this).randomId());
			add(routeBuilder);
			return this;
		}

		/**
		 * Builds and returns a {@link RouteLocator}
		 * @return a {@link RouteLocator}
		 */
		public RouteLocator build() {
			return () -> Flux.fromIterable(this.routes).map(routeBuilder -> routeBuilder.build());
		}

		ConfigurableApplicationContext getContext() {
			return context;
		}

		/***
		 * routes添加一个路由route
		 * @param route
		 */
		void add(Route.AsyncBuilder route) {
			routes.add(route);
		}
	}

	/***
	 * 这是一个Route标准对象类型
	 */
	public static class RouteSpec {
		/***
		 * 初始化一个AsyncBuilder对象，也就是路由的构建器
		 */
		private final Route.AsyncBuilder routeBuilder = Route.async();
		/***
		 * 又捡起
		 */
		private final Builder builder;

		RouteSpec(Builder builder) {
			this.builder = builder;
		}

		/***
		 * 创建PredicateSpec对象
		 * @param id
		 * @return
		 */
		public PredicateSpec id(String id) {
			this.routeBuilder.id(id);
			return predicateBuilder();
		}

		public PredicateSpec randomId() {
			return id(UUID.randomUUID().toString());
		}

		private PredicateSpec predicateBuilder() {
			return new PredicateSpec(this.routeBuilder, this.builder);
		}

	}


}
