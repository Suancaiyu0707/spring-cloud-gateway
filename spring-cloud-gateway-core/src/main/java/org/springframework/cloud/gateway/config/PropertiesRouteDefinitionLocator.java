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

package org.springframework.cloud.gateway.config;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 * 从配置文件(yml/properties) 读取路由配置
 */
public class PropertiesRouteDefinitionLocator implements RouteDefinitionLocator {
	/***
	 * 网关配置
	 * 	eg：
	 * 	GatewayProperties{
	 * 		routes=[
	 * 			RouteDefinition{
	 * 				id='cookie-route',
	 * 				predicates=[
	 * 					PredicateDefinition{
	 * 						name='Cookie', args={_genkey_0=username, _genkey_1=xuzf}
	 * 					}
	 * 				],
	 * 				filters=[
	 * 					FilterDefinition{
	 * 						name='AddRequestHeader', args={_genkey_0=X-Request-Foo, _genkey_1=Bar}
	 * 					}
	 * 				],
	 * 				uri=http://example.org,
	 * 				order=0},
	 * 			RouteDefinition{
	 * 				id='default_path_to_httpbin',
	 * 				predicates=[
	 * 					PredicateDefinition{
	 * 						name='Path',
	 * 						args={_genkey_0=/**}
	 * 					}
	 * 				],
	 * 				filters=[],
	 * 				uri=http://example.org,
	 * 				order=10000
	 * 			}
	 * 		],
	 * 		defaultFilters=[
	 * 			FilterDefinition{name='PrefixPath', args={_genkey_0=/httpbin}},
	 * 			FilterDefinition{name='AddResponseHeader', args={_genkey_0=X-Response-Default-Foo, _genkey_1=Default-Bar}}], streamingMediaTypes=[text/event-stream, application/stream+json
	 * 		]
	 * 	}
	 */
	private final GatewayProperties properties;

	public PropertiesRouteDefinitionLocator(GatewayProperties properties) {
		this.properties = properties;
	}

	/***
	 * 从 GatewayProperties 获取路由配置数组
	 * @return
	 */
	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return Flux.fromIterable(this.properties.getRoutes());
	}
}
