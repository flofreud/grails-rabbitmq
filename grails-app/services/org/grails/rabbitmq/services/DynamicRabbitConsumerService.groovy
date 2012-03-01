package org.grails.rabbitmq.services

import org.grails.rabbitmq.ClosureMessageConverter
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter
import org.grails.rabbitmq.RabbitConfigurationHolder

class DynamicRabbitConsumerService {

    static transactional = false

    def rabbitMQConnectionFactory
    RabbitConfigurationHolder rabbitConfigurationHolder

    private SimpleMessageListenerContainer setupListener(MessageListener adapter, String queue, Map options) {
        def listener = new SimpleMessageListenerContainer()
        listener.autoStartup = false
        listener.channelTransacted = (options.channelTransacted == null) ? false : options.channelTransacted
        listener.connectionFactory = rabbitMQConnectionFactory
        listener.concurrentConsumers = options.concurrentConsumers ?: 1
        listener.setQueueNames(queue)
        listener.messageListener = adapter
        return listener
    }

    private void handleMessage(Message message, Closure closure) {
        String body = new String(message.body)
        closure.call(body)
    }

    SimpleMessageListenerContainer createMessageListenerFromClosure(Map options = [:], String queueName, Closure closure) {
        if (options.messageListener != null && options.messageListener) {
            def adapter = [onMessage: closure] as MessageListener
            return setupListener(adapter, queueName, options)
        } else {
            def adapter = new ClosureMessageConverter(closure: closure)
            return setupListener(adapter, queueName, options)
        }
    }

    SimpleMessageListenerContainer createQueueListenerFromInstance(Map options = [:], Object instance) {
        def adapter = new MessageListenerAdapter()
        def queue = options.queueName ?: rabbitConfigurationHolder.getServiceQueueName(instance)
        options.concurrentConsumers = options.concurrentConsumers ?: rabbitConfigurationHolder.getServiceConcurrentConsumers(instance)
        options.channelTransacted = (options.channelTransacted != null)? options.channelTransacted : rabbitConfigurationHolder.isServiceTransactional(instance)
        if(queue == null){
            throw new IllegalArgumentException("Unable to determine queue name to listen to.")
        }
        adapter.delegate = instance
        return setupListener(adapter, queue, options)
    }

}
