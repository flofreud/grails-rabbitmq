package org.grails.rabbitmq

import grails.test.GrailsUnitTestCase
import org.codehaus.groovy.grails.commons.GrailsClass

class RabbitConfigurationHolderTest extends GrailsUnitTestCase {

    def disabledConfig = toConfigHolder("""
        rabbitmq {
            disableListening = true
            concurrentConsumers = 2
            services {
                redService {
                    concurrentConsumers = 3
                    disableListening = false
                    transactional = true
                }
                blueService {
                    disableListening = true
                }
            }
        }
    """)

    def enabledConfig = toConfigHolder("""
        rabbitmq {

            // outside of services block for backwards compatibility
            redService {
                transactional = true
            }

            services {
                redService {
                    concurrentConsumers = 5
                    disableListening = false
                }
                blueService {
                    disableListening = true
                }
            }
        }
    """)

    def blueService = new MockListener(propertyName: 'blueService', transactional: false, rabbitQueue: 'blueQueue')
    def redService = new MockListener(propertyName: 'redService', transactional: false, rabbitQueue: 'redQueue')
    def pinkService = new MockListener(propertyName: 'pinkService', transactional: true, rabbitQueue: 'pinkQueue')

    def toConfigHolder(String config) {
        return new RabbitConfigurationHolder(new ConfigSlurper().parse(config).rabbitmq)
    }

    void testGetDefaultConcurrentConsumers() {
        assert disabledConfig.defaultConcurrentConsumers == 2
        assert enabledConfig.defaultConcurrentConsumers == 1
    }

    void testServiceConcurrentConsumers() {
        assert disabledConfig.getServiceConcurrentConsumers(redService) == 3
        assert disabledConfig.getServiceConcurrentConsumers(blueService) == 2
        assert disabledConfig.getServiceConcurrentConsumers(pinkService) == 2

        assert enabledConfig.getServiceConcurrentConsumers(redService) == 5
        assert enabledConfig.getServiceConcurrentConsumers(blueService) == 1
        assert enabledConfig.getServiceConcurrentConsumers(pinkService) == 1
        assert enabledConfig.getServiceConcurrentConsumers(new Object()) == 1
    }

    void testListeningDisabled() {
        assert disabledConfig.listeningDisabled
        assert !enabledConfig.listeningDisabled
    }

    void testServiceEnabled() {
        assert !disabledConfig.isServiceEnabled(redService)
        assert !disabledConfig.isServiceEnabled(blueService)
        assert !disabledConfig.isServiceEnabled(pinkService)

        assert enabledConfig.isServiceEnabled(redService)
        assert !enabledConfig.isServiceEnabled(blueService)
        assert enabledConfig.isServiceEnabled(pinkService)
    }

    void testIsServiceTransactional() {
        assert ! enabledConfig.isServiceTransactional(blueService)
        assert enabledConfig.isServiceTransactional(redService)
        assert enabledConfig.isServiceTransactional(pinkService)
        assert !enabledConfig.isServiceTransactional(new Object())
        assert disabledConfig.isServiceTransactional(redService)
    }

}

class MockListener {
    def rabbitQueue = 'blueQueue'
    def transactional = false
    def propertyName = 'blueService'
}
