package org.grails.rabbitmq.services

import grails.test.GrailsUnitTestCase
import org.grails.rabbitmq.RabbitConfigurationHolder
import org.springframework.amqp.rabbit.connection.RabbitAccessor
import org.grails.rabbitmq.MockListener

class DynamicRabbitConsumerServiceUnitTests extends GrailsUnitTestCase {

    def defaultConfig = toConfigHolder("""
        rabbitmq {
            concurrentConsumers = 2
            services {
                redService {
                    rabbitQueue = 'redQueue'
                    concurrentConsumers = 3
                    disableListening = false
                }
                blueService {
                    disableListening = true
                }
            }
        }
    """)    
    
    def toConfigHolder(String config) {
        return new RabbitConfigurationHolder(new ConfigSlurper().parse(config).rabbitmq)
    }
    
    void testDefaultingOnInstanceCreation(){
        def dynamicListenerService = new DynamicRabbitConsumerService()
        dynamicListenerService.rabbitConfigurationHolder = defaultConfig
        shouldFail(IllegalArgumentException) {
            dynamicListenerService.createQueueListenerFromInstance(new Object())
        }

        def listener = dynamicListenerService.createQueueListenerFromInstance(new Object(), queueName: 'test')
        assert listener.queueNames.length == 1
        assert listener.queueNames[0] == 'test'
        assert listener.concurrentConsumers == 2
        assert ! listener.isChannelTransacted()

        def listener2 = dynamicListenerService.createQueueListenerFromInstance(new Object(), queueName: 'test', channelTransacted: true)
        assert listener2.isChannelTransacted()
        
        def fakeListener = new MockListener(transactional: true, propertyName: 'redService')
        def listener3 = dynamicListenerService.createQueueListenerFromInstance(fakeListener)
        assert listener3.concurrentConsumers == 3
        assert listener3.isChannelTransacted() == true
        assert listener3.queueNames[0] == 'redQueue'
    }
    
    void testDefaultingOnCloserCreation() {
        def dynamicListenerService = new DynamicRabbitConsumerService()
        dynamicListenerService.rabbitConfigurationHolder = defaultConfig
        
        def listener = dynamicListenerService.createMessageListenerFromClosure('test'){ ignore ->}
        assert listener.concurrentConsumers == 1
        assert ! listener.isChannelTransacted()
        assert listener.queueNames[0] == 'test'
        
        def listener2 = dynamicListenerService.createMessageListenerFromClosure('test', channelTransacted: true, concurrentConsumers: 2){ ignore ->}
        assert listener2.concurrentConsumers == 2
        assert listener2.isChannelTransacted()
    }
    
}
