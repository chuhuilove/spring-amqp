/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.amqp.support.converter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.utils.SerializationUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.remoting.rmi.CodebaseAwareObjectInputStream;
import org.springframework.util.ClassUtils;

/**
 * {@link MessageConverter}接口的一个实现,它可以处理字符串,可序列化实例或字节数组.
 * {@link #toMessage(Object, MessageProperties)}方法只是检查提供的实例的类型，
 * 而{@link #fromMessage(Message)}方法依赖于提供的消息的{@link MessageProperties#getContentType() content-type}.
 *
 * Implementation of {@link MessageConverter} that can work with Strings, Serializable
 * instances, or byte arrays. The {@link #toMessage(Object, MessageProperties)} method
 * simply checks the type of the provided instance while the {@link #fromMessage(Message)}
 * method relies upon the {@link MessageProperties#getContentType() content-type} of the
 * provided Message.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 */
public class SimpleMessageConverter extends WhiteListDeserializingMessageConverter implements BeanClassLoaderAware {

	public static final String DEFAULT_CHARSET = "UTF-8";

	private volatile String defaultCharset = DEFAULT_CHARSET;

	private String codebaseUrl;

	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	/**
	 * Set the codebase URL to download classes from if not found locally. Can consists of multiple URLs, separated by
	 * spaces.
	 * <p>
	 * Follows RMI's codebase conventions for dynamic class download.
	 *
	 * @param codebaseUrl The codebase URL.
	 *
	 * @see org.springframework.remoting.rmi.CodebaseAwareObjectInputStream
	 * @see java.rmi.server.RMIClassLoader
	 */
	public void setCodebaseUrl(String codebaseUrl) {
		this.codebaseUrl = codebaseUrl;
	}

	/**
	 * Specify the default charset to use when converting to or from text-based
	 * Message body content. If not specified, the charset will be "UTF-8".
	 *
	 * @param defaultCharset The default charset.
	 */
	public void setDefaultCharset(String defaultCharset) {
		this.defaultCharset = (defaultCharset != null) ? defaultCharset : DEFAULT_CHARSET;
	}

	/**
	 * 从AMQP消息转换到一个对象.
	 */
	@Override
	public Object fromMessage(Message message) throws MessageConversionException {
		/**
		 * 转换过程:
		 * 先从Message中尝试获取MessageProperties,如果没有获取到,
		 * 则直接把Message中的{@link Message#getBody()}提取出来,然后进行返回.
		 * 如果有MessageProperties,则:
		 * 先获取content type,以判断该消息体是一个什么类型,
		 * 如果content type 不为空,并且content type是以text开头,则直接将消息体转换成字符串,返出去.
		 * 如果content type 不为空,并且content type是{@link MessageProperties#CONTENT_TYPE_SERIALIZED_OBJECT},
		 * 意味着,消息体中是一个java对象,需要将其进行反序列化,返出去.
		 */
		Object content = null;
		MessageProperties properties = message.getMessageProperties();
		if (properties != null) {
			String contentType = properties.getContentType();
			if (contentType != null && contentType.startsWith("text")) {
				String encoding = properties.getContentEncoding();
				if (encoding == null) {
					encoding = this.defaultCharset;
				}
				try {
					content = new String(message.getBody(), encoding);
				}
				catch (UnsupportedEncodingException e) {
					throw new MessageConversionException(
							"failed to convert text-based Message content", e);
				}
			}
			else if (contentType != null &&
					contentType.equals(MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT)) {
				try {
					content = SerializationUtils.deserialize(
							createObjectInputStream(new ByteArrayInputStream(message.getBody()), this.codebaseUrl));
				}
				catch (IOException | IllegalArgumentException | IllegalStateException e) {
					throw new MessageConversionException(
							"failed to convert serialized Message content", e);
				}
			}
		}
		if (content == null) {
			content = message.getBody();
		}
		return content;
	}

	/**
	 * 从提供的object中创建一个AMQP Message
	 */
	@Override
	protected Message createMessage(Object object, MessageProperties messageProperties) throws MessageConversionException {
		/**
		 * 如果是一个字节数组,则设置content type为application/octet-stream
		 * 如果是一个字符串,则直接将字符串转化成byte[]content type为 text/plain
		 * 如果实现了Serializable接口,代表其可以进行序列化传输.content type为application/x-java-serialized-object
		 *
		 * 最后,从产生的byte[]和messageProperties,创建一个Message对象.
		 */
		byte[] bytes = null;
		if (object instanceof byte[]) {
			bytes = (byte[]) object;
			messageProperties.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
		}
		else if (object instanceof String) {
			try {
				bytes = ((String) object).getBytes(this.defaultCharset);
			}
			catch (UnsupportedEncodingException e) {
				throw new MessageConversionException(
						"failed to convert to Message content", e);
			}
			messageProperties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
			messageProperties.setContentEncoding(this.defaultCharset);
		}
		else if (object instanceof Serializable) {
			try {
				bytes = SerializationUtils.serialize(object);
			}
			catch (IllegalArgumentException e) {
				throw new MessageConversionException(
						"failed to convert to serialized Message content", e);
			}
			messageProperties.setContentType(MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT);
		}
		if (bytes != null) {
			messageProperties.setContentLength(bytes.length);
			return new Message(bytes, messageProperties);
		}
		throw new IllegalArgumentException(getClass().getSimpleName()
				+ " only supports String, byte[] and Serializable payloads, received: " + object.getClass().getName());
	}

	/**
	 * Create an ObjectInputStream for the given InputStream and codebase. The default implementation creates a
	 * CodebaseAwareObjectInputStream.
	 * @param is the InputStream to read from
	 * @param codebaseUrl the codebase URL to load classes from if not found locally (can be <code>null</code>)
	 * @return the new ObjectInputStream instance to use
	 * @throws IOException if creation of the ObjectInputStream failed
	 * @see org.springframework.remoting.rmi.CodebaseAwareObjectInputStream
	 */
	protected ObjectInputStream createObjectInputStream(InputStream is, String codebaseUrl) throws IOException {
		return new CodebaseAwareObjectInputStream(is, this.beanClassLoader, codebaseUrl) {

			@Override
			protected Class<?> resolveClass(ObjectStreamClass classDesc) throws IOException, ClassNotFoundException {
				Class<?> clazz = super.resolveClass(classDesc);
				checkWhiteList(clazz);
				return clazz;
			}

		};
	}

}
