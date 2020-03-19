/*
 * Copyright 2017-2018 the original author or authors.
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

/**
 * A marker interface for data used to correlate information about sent messages.
 * One example might be used to correlate a send confirmation.
 * 用于关联有关发送消息的信息的数据的标记接口.
 * 可以使用一个示例来关联发送确认.
 * 数据的标记接口,用于关联已发送消息的信息.
 * 可以使用一个示例来关联发送确认.
 *
 * @author Gary Russell
 * @since 1.6.7
 *
 */
public interface Correlation {

}
