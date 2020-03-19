/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.amqp.core;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.amqp.utils.SerializationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 *
 * 0-8和0-9-1 AMQP 规范没有定义一个Message类或接口.
 * 而是在执行诸如basicPublish之类的操作时,将内容作为字节数组参数传递,并将其他属性作为单独的参数传递.
 * Spring AMQP将Message类定义为更通用的AMQP域模型表示形式的一部分.
 * Message类的目的是简单地将主体和属性封装在单个实例中,这样AMQP API的其余部分就可以变得更简单.
 * @author Mark Pollack
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Dave Syer
 * @author Gary Russell
 * @author Alex Panchenko
 * @author Artem Bilan
 */
public class Message implements Serializable {

	private static final long serialVersionUID = -7177590352110605597L;

	private static final String ENCODING = Charset.defaultCharset().name();

	private static final Set<String> whiteListPatterns = // NOSONAR lower case static
			new LinkedHashSet<>(Arrays.asList("java.util.*", "java.lang.*"));

	private final MessageProperties messageProperties;

	private final byte[] body;

	public Message(byte[] body, MessageProperties messageProperties) { //NOSONAR
		this.body = body; //NOSONAR
		this.messageProperties = messageProperties;
	}

	/**
	 * Add patterns to the white list of permissable package/class name patterns for
	 * deserialization in {@link #toString()}.
	 * The patterns will be applied in order until a match is found.
	 * A class can be fully qualified or a wildcard '*' is allowed at the
	 * beginning or end of the class name.
	 * Examples: {@code com.foo.*}, {@code *.MyClass}.
	 * By default, only {@code java.util} and {@code java.lang} classes will be
	 * deserialized.
	 * @param patterns the patterns.
	 * @since 1.5.7
	 */
	public static void addWhiteListPatterns(String... patterns) {
		Assert.notNull(patterns, "'patterns' cannot be null");
		whiteListPatterns.addAll(Arrays.asList(patterns));
	}

	public byte[] getBody() {
		return this.body; //NOSONAR
	}

	public MessageProperties getMessageProperties() {
		return this.messageProperties;
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		buffer.append("(");
		buffer.append("Body:'").append(this.getBodyContentAsString()).append("'");
		if (this.messageProperties != null) {
			buffer.append(" ").append(this.messageProperties.toString());
		}
		buffer.append(")");
		return buffer.toString();
	}

	private String getBodyContentAsString() {
		if (this.body == null) {
			return null;
		}
		try {
			String contentType = (this.messageProperties != null) ? this.messageProperties.getContentType() : null;
			if (MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT.equals(contentType)) {
				return SerializationUtils.deserialize(new ByteArrayInputStream(this.body), whiteListPatterns,
						ClassUtils.getDefaultClassLoader()).toString();
			}
			if (MessageProperties.CONTENT_TYPE_TEXT_PLAIN.equals(contentType)
					|| MessageProperties.CONTENT_TYPE_JSON.equals(contentType)
					|| MessageProperties.CONTENT_TYPE_JSON_ALT.equals(contentType)
					|| MessageProperties.CONTENT_TYPE_XML.equals(contentType)) {
				return new String(this.body, ENCODING);
			}
		}
		catch (Exception e) {
			// ignore
		}
		// Comes out as '[B@....b' (so harmless)
		return this.body.toString() + "(byte[" + this.body.length + "])"; //NOSONAR
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(this.body);
		result = prime * result + ((this.messageProperties == null) ? 0 : this.messageProperties.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Message other = (Message) obj;
		if (!Arrays.equals(this.body, other.body)) {
			return false;
		}
		if (this.messageProperties == null) {
			if (other.messageProperties != null) {
				return false;
			}
		}
		else if (!this.messageProperties.equals(other.messageProperties)) {
			return false;
		}
		return true;
	}


}
