package org.grails.rabbitmq

import org.codehaus.groovy.grails.commons.GrailsClassUtils as GCU

/**
 * Used to pull configuration logic out of the main plugin file for
 * testability and reuse.
 */
class RabbitConfigurationHolder {

    def rabbitmqConfig

    public RabbitConfigurationHolder(rabbitmqConfig) {
        this.rabbitmqConfig = rabbitmqConfig
    }

    int getDefaultConcurrentConsumers() {
        return rabbitmqConfig.concurrentConsumers ?: 1
    }

    int getServiceConcurrentConsumers(service) {
        def concurrentConsumers = defaultConcurrentConsumers

        if(service.hasProperty('propertyName')){        
            def propertyName = service.propertyName
            if(!(rabbitmqConfig.services."${propertyName}".concurrentConsumers instanceof ConfigObject)){
                concurrentConsumers = rabbitmqConfig.services."${propertyName}".concurrentConsumers as int
            }
        }
        return concurrentConsumers 
    }

    /**
     * Lookup the queue name of the listener, or return null if it is not found.
     */
    String getServiceQueueName(service) {
        def clazz = (service.hasProperty('clazz'))? service.clazz : service.class
        String rabbitQueue = GCU.getStaticPropertyValue(clazz, 'rabbitQueue')

        if(service.hasProperty('propertyName')){
            def propertyName = service.propertyName
            if (!(rabbitmqConfig.services."${propertyName}".rabbitQueue instanceof ConfigObject)) {
                rabbitQueue = rabbitmqConfig.services."${propertyName}".rabbitQueue as String
            }
        }
        return rabbitQueue
    }

    boolean isServiceTransactional(service) {
        def transactional = false
        if(service.hasProperty('transactional')){
            transactional = service.transactional
        }
        if(service.hasProperty('propertyName')){
            def propertyName = service.propertyName

            // for backwards compatibility use services version going forward
            if (!(rabbitmqConfig."${propertyName}".transactional instanceof ConfigObject)) {
                transactional = rabbitmqConfig."${propertyName}".transactional as Boolean
            }
            if (!(rabbitmqConfig.services."${propertyName}".transactional instanceof ConfigObject)) {
                transactional = rabbitmqConfig.services."${propertyName}".transactional as Boolean
            }
        }
        return transactional
    }

    boolean isListeningDisabled() {
        return rabbitmqConfig.disableListening
    }

    boolean isServiceEnabled(def service) {
        if (listeningDisabled) return false
        def propertyName = service.propertyName
        return !rabbitmqConfig.services?."${propertyName}"?.disableListening
    }

}
