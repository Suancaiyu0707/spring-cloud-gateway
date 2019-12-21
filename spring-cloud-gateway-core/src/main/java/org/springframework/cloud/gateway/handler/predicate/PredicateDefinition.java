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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.validation.ValidationException;
import javax.validation.constraints.NotNull;

import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.validation.annotation.Validated;

import static org.springframework.util.StringUtils.tokenizeToStringArray;

/**
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       - id: cookie-route
 *         uri: http://example.org
 *         predicates:
 *         - Cookie=username, xuzf
 *         filters:
 *         - AddRequestHeader=X-Request-Foo, Bar
 *
 * @author Spencer Gibb
 * 断言的实体类
 */
@Validated
public class PredicateDefinition {
	/**
	 * 定义了Predicate类型的名称，必须符合特定的命名规范，为对应的工厂名前缀
	 * 	CookieRoutePredicateFactory==>Cookie
	 * 	name：Cookie
	 */
	@NotNull
	private String name;
	/***
	 * 一个键值对参数用于构造 Predicate 对象
	 * eg:
	 * 	- Cookie=username, xuzf #这里的username, xuzf会被解析到args里
	 * 	args：{"_genkey_0":"username","_genkey_1":"xuzf"}
	 */
	private Map<String, String> args = new LinkedHashMap<>();

	public PredicateDefinition() {
	}

	/***
	 *
	 * @param text eg: - Cookie=username, xuzf
	 */
	public PredicateDefinition(String text) {
		//获得'='位置
		int eqIdx = text.indexOf('=');
		if (eqIdx <= 0) {
			throw new ValidationException("Unable to parse PredicateDefinition text '" + text + "'" +
					", must be of the form name=value");
		}
		//截取'='前的值，设置为name属性，也就是Predicate类型名称，比如：Cookie
		setName(text.substring(0, eqIdx));
		//处理'='后的参数： username, xuzf。会被转成{"_genkey_0":"username","_genkey_1":"xuzf"}
		String[] args = tokenizeToStringArray(text.substring(eqIdx+1), ",");

		for (int i=0; i < args.length; i++) {
			this.args.put(NameUtils.generateName(i), args[i]);
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, String> getArgs() {
		return args;
	}

	public void setArgs(Map<String, String> args) {
		this.args = args;
	}

	public void addArg(String key, String value) {
		this.args.put(key, value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PredicateDefinition that = (PredicateDefinition) o;
		return Objects.equals(name, that.name) &&
				Objects.equals(args, that.args);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, args);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("PredicateDefinition{");
		sb.append("name='").append(name).append('\'');
		sb.append(", args=").append(args);
		sb.append('}');
		return sb.toString();
	}
}
