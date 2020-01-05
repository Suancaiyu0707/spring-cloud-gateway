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

import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyPredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.toAsyncPredicate;

/**
 * .route(
*       p -> p.path("/get") //添加断言predicates，定义特定的规则，由具体的route去处理
*       .filters(f->f.addRequestHeader("Hello","World"))//filters是各种过滤器，用来对请求做各种判断和修改
*       .uri("http://httpbin.org:80")
*                     )
*                     .route(
*                            p -> p
*                             .host("*.hystrix.com")
*                             .filters(f->f
*                                 .hystrix(config -> config
*                                     .setName("xuzfcmd")
*                                     .setFallbackUri("forward:/fallback")
*                                 ))
*                             .uri("http://httpbin.org:80")
*
*                     )
 * Predicates that can be applies to a URI route.
 * PredicateSpec 会根据上断言定义或断言的配置返回一个BooleanSpec
 */
public class PredicateSpec extends UriSpec {

	PredicateSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
	}

	public PredicateSpec order(int order) {
		this.routeBuilder.order(order);
		return this;
	}

	public BooleanSpec predicate(Predicate<ServerWebExchange> predicate) {
		return asyncPredicate(toAsyncPredicate(predicate));
	}

	public BooleanSpec asyncPredicate(AsyncPredicate<ServerWebExchange> predicate) {
		this.routeBuilder.asyncPredicate(predicate);
		return new BooleanSpec(this.routeBuilder, this.builder);
	}

	protected GatewayFilterSpec createGatewayFilterSpec() {
		return new GatewayFilterSpec(this.routeBuilder, this.builder);
	}

	/**
	 *	返回一个用于检查请求是否在特定的时间之后
	 * @param datetime 用于和请求时间进行比较
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec after(ZonedDateTime datetime) {
		return asyncPredicate(getBean(AfterRoutePredicateFactory.class)
				.applyAsync(c-> c.setDatetime(datetime.toString())));
	}

	/**
	 * 返回一个用于检查请求是否在特定的时间之前
	 * @param datetime 用于和请求时间进行比较
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec before(ZonedDateTime datetime) {
		return asyncPredicate(getBean(BeforeRoutePredicateFactory.class).applyAsync(c -> c.setDatetime(datetime.toString())));
	}

	/***
	 * 返回一个用于检查请求是否在特定的时间之间
	 * @param datetime1 用于和请求时间进行比较
	 * @param datetime2 用于和请求时间进行比较
	 * @return
	 */
	public BooleanSpec between(ZonedDateTime datetime1, ZonedDateTime datetime2) {
		return asyncPredicate(getBean(BetweenRoutePredicateFactory.class)
				.applyAsync(c -> c.setDatetime1(datetime1.toString()).setDatetime2(datetime2.toString())));
	}

	/**
	 * 返回一个用于检查请求中的特定cookie是否满足某个规范
	 * @param name 指定判断的cookie的名称
	 * @param regex 指定判断的cookie的值是否符合某种表达式
	 * @return
	 */
	public BooleanSpec cookie(String name, String regex) {
		return asyncPredicate(getBean(CookieRoutePredicateFactory.class)
				.applyAsync(c -> c.setName(name).setRegexp(regex)));
	}

	/***
	 * 返回一个用于检查请求的requestheader是否存在某个header
	 * @param header 检查的header
	 * @return
	 */
	public BooleanSpec header(String header) {
		return asyncPredicate(getBean(HeaderRoutePredicateFactory.class)
				.applyAsync(c -> c.setHeader(header))); //TODO: default regexp
	}

	/***
	 * 一个用于检查请求的中的header的值是否匹配某个模式的断言
	 * @param header 校验的header
	 * @param regex header的值匹配的模式
	 * @return
	 */
	public BooleanSpec header(String header, String regex) {
		return asyncPredicate(getBean(HeaderRoutePredicateFactory.class)
				.applyAsync(c -> c.setHeader(header).setRegexp(regex)));
	}

	/**
	 * A predicate that checks if the {@code host} header matches a given pattern
	 * @param pattern the pattern to check against.  The pattern is an Ant style pattern with {@code .} as a separator
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	/***
	 * 一个用于检查名叫host的header的值是否满足某个模式的断言
	 * @param pattern 匹配的模式
	 * @return
	 */
	public BooleanSpec host(String pattern) {
		return asyncPredicate(getBean(HostRoutePredicateFactory.class)
				.applyAsync(c-> c.setPattern(pattern)));
	}

	/**
	 * 一个用户检查提交方式是否满足某个方式的断言
	 * @param method 提交方式，比如POST、GET等
	 * @return
	 */
	public BooleanSpec method(String method) {
		return asyncPredicate(getBean(MethodRoutePredicateFactory.class)
				.applyAsync(c -> c.setMethod(HttpMethod.resolve(method))));
	}
	/***
	 * 一个用于检查HttpMethod是否是指定的Method
	 * @param method 指定的Method，比如GET/PUT/DELETE
	 * @return
	 */
	public BooleanSpec method(HttpMethod method) {
		return asyncPredicate(getBean(MethodRoutePredicateFactory.class)
				.applyAsync(c -> c.setMethod(method)));
	}

	/***
	 * 一个用于检查请求的path路径是否符合某个规范的断言
	 * @param pattern 路径模式
	 * @return
	 */
	public BooleanSpec path(String pattern) {
		//会
		return asyncPredicate(
				getBean(PathRoutePredicateFactory.class)//获取路由的断言工厂，这里是PathRoutePredicateFactory
				.applyAsync(
						c -> c.setPattern(pattern)//创建一个Consumer对象，并设置这个Config的pattern。这里的config是PathRoutePredicateFactory。Config
				));
	}

	/***
	 * 一个用于检查请求的path路径是否符合某个规范的断言
	 * @param pattern 路径模式
	 * @return
	 */
	public BooleanSpec path(String pattern, boolean matchOptionalTrailingSeparator) {
		return asyncPredicate(getBean(PathRoutePredicateFactory.class)
				.applyAsync(c -> c.setPattern(pattern).setMatchOptionalTrailingSeparator(matchOptionalTrailingSeparator)));
	}

	/**
	 * This predicate is BETA and may be subject to change in a future release.
	 * A predicate that checks the contents of the request body
	 * @param inClass the class to parse the body to
	 * @param predicate a predicate to check the contents of the body
	 * @param <T> the type the body is parsed to
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public <T> BooleanSpec readBody(Class<T> inClass, Predicate<T> predicate) {
		return asyncPredicate(getBean(ReadBodyPredicateFactory.class)
				.applyAsync(c -> c.setPredicate(inClass, predicate)));
	}

	/**
	 * A predicate that checks if a query parameter matches a regular expression
	 * @param param the query parameter name
	 * @param regex the regular expression to evaluate the query parameter value against
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec query(String param, String regex) {
		return asyncPredicate(getBean(QueryRoutePredicateFactory.class)
				.applyAsync(c -> c.setParam(param).setRegexp(regex)));
	}

	/***
	 * 一个用于检查request url中是否存在指定的查询参数
	 * @param param 指定的查询参数
	 * @return
	 */
	public BooleanSpec query(String param) {
		return asyncPredicate(getBean(QueryRoutePredicateFactory.class)
				.applyAsync(c -> c.setParam(param)));
	}

	/**
	 * A predicate which checks the remote address of the request.
	 * By default the RemoteAddr Route Predicate Factory uses the remote address from the incoming request.
	 * This may not match the actual client IP address if Spring Cloud Gateway sits behind a proxy layer.
	 * Use {@link PredicateSpec#remoteAddr(RemoteAddressResolver, String...)} to customize the resolver.
	 * You can customize the way that the remote address is resolved by setting a custom RemoteAddressResolver.

	 * @param addrs the remote address to verify.  Should use CIDR-notation (IPv4 or IPv6) strings.
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec remoteAddr(String... addrs) {
		return remoteAddr(null, addrs);
	}

	/**
	 * 一个用于匹配请求的地址在指定的地址范围内
	 * @param resolver
	 * @param addrs
	 * @return
	 */
	public BooleanSpec remoteAddr(RemoteAddressResolver resolver, String... addrs) {
		return asyncPredicate(getBean(RemoteAddrRoutePredicateFactory.class).applyAsync(c -> {
			c.setSources(addrs);
			if (resolver != null) {
				c.setRemoteAddressResolver(resolver);
			}
		}));
	}

	/**
	 * A predicate which will select a route based on its assigned weight.  The
	 * @param group the group the route belongs to
	 * @param weight the weight for the route
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec weight(String group, int weight) {
		return asyncPredicate(getBean(WeightRoutePredicateFactory.class)
				.applyAsync(c -> c.setGroup(group)
						.setRouteId(routeBuilder.getId())
						.setWeight(weight)));
	}

	public BooleanSpec cloudFoundryRouteService() {
		return predicate(
				getBean(CloudFoundryRouteServiceRoutePredicateFactory.class).apply(c -> {
				}));
	}

	/**
	 * A predicate which is always true
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec alwaysTrue() {
		return predicate(exchange -> true);
	}
}
