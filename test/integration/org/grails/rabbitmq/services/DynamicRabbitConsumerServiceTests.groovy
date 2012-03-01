package org.grails.rabbitmq.services

import grails.test.GrailsUnitTestCase
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.SimpleMessageConverter

/**
 * This classes startup dynamic listeners on a real rabbitmq instance,
 * which is defined in the test config section of the plugin configuration.
 */
class DynamicRabbitConsumerServiceTests extends GrailsUnitTestCase {

    def dynamicRabbitConsumerService
    RabbitAdmin adm
    RabbitTemplate rabbitTemplate
    def grailsApplication

    SimpleMessageConverter converter = new SimpleMessageConverter()
    String messageText = 'HELLO WORLD'

    void testDynamicStringListenerClosureCreation() {
        String receivedMessage = null
        Queue queue = adm.declareQueue()
        rabbitTemplate.convertAndSend('', queue.name, messageText)
        def listener = dynamicRabbitConsumerService.createMessageListenerFromClosure(queue.name) { incoming ->
            receivedMessage = incoming
        }
        try {
            listener.start()
            for (int i = 0; i < 8; i++) {
                sleep(100)
                if (receivedMessage != null) {
                    assert receivedMessage.class == String.class
                    assert receivedMessage == messageText
                }
            }
            if (receivedMessage == null) {
                fail("Failed to get expected message from queue")
            }
        } finally {
            if (listener) {
                listener.shutdown()
            }
        }
    }

    void testDynamicMessageListenerClosureCreation() {
        Message receivedMessage = null
        Queue queue = adm.declareQueue()
        rabbitTemplate.convertAndSend('', queue.name, messageText)
        def listener = dynamicRabbitConsumerService.createMessageListenerFromClosure(queue.name, messageListener: true) { incoming ->
            receivedMessage = incoming
        }
        try {
            listener.start()
            for (int i = 0; i < 8; i++) {
                sleep(100)
                if (receivedMessage != null) {
                    assert new String(receivedMessage.body) == messageText
                }
            }
            if (receivedMessage == null) {
                fail("Failed to get expected message from queue")
            }
        } finally {
            if (listener) {
                listener.shutdown()
            }
        }
    }

    void testDynamicMessageListenerInstanceCreation() {
        def service = new MockQueueService()
        Queue queue = new Queue('org.grails.rabbitmq.service.BlueQueue', false, false, true)
        adm.declareQueue(queue)
        adm.purgeQueue('org.grails.rabbitmq.service.BlueQueue', false)
        rabbitTemplate.convertAndSend('', queue.name, messageText)
        def listener = dynamicRabbitConsumerService.createQueueListenerFromInstance(service)
        assert listener.queueNames.length == 1
        assert listener.queueNames[0] == 'org.grails.rabbitmq.service.BlueQueue'
        try {
            listener.start()
            for (int i = 0; i < 8; i++) {
                sleep(100)
                if (service.messages.size()) {
                    assert service.messages.size() == 1
                    assert new String(service.messages[0].body) == messageText
                }
            }
            if (service.messages.size() == 0) {
                fail("Failed to get expected message from queue")
            }
        } finally {
            if (listener) {
                listener.shutdown()
            }
        }
    }
}

class MockQueueService implements MessageListener {
    static rabbitQueue = 'org.grails.rabbitmq.service.BlueQueue'
    static transactional = false
    def messages = []

    void onMessage(Message message) {
        messages << message
    }

}