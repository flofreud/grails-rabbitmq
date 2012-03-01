package org.grails.rabbitmq

import org.springframework.amqp.core.Message
import org.springframework.amqp.support.converter.SimpleMessageConverter
import org.springframework.amqp.core.MessageListener

/**
 * A class used to convert {@link Message} objects into string messages.
 * Used by the {@link org.grails.rabbitmq.services.DynamicRabbitConsumerService}
 */
class ClosureMessageConverter implements MessageListener {
    Closure closure
    static private SimpleMessageConverter converter = new SimpleMessageConverter()

    void onMessage(Message message) {
        def obj = converter.fromMessage(message)
        closure.call(obj)
    }
}
