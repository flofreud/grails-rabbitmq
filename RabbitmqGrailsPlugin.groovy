import org.codehaus.groovy.grails.commons.GrailsClassUtils as GCU

import org.grails.rabbitmq.AutoQueueMessageListenerContainer
import org.grails.rabbitmq.RabbitDynamicMethods
import org.grails.rabbitmq.RabbitQueueBuilder
import org.grails.rabbitmq.DefaultErrorHandler
import org.grails.rabbitmq.RabbitConfigurationHolder
import org.grails.rabbitmq.services.DynamicRabbitConsumerService
import org.grails.rabbitmq.RabbitConfigurationHolder

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter
import org.springframework.amqp.support.converter.MessageConverter
import static org.springframework.amqp.core.Binding.DestinationType.QUEUE
import org.springframework.util.ErrorHandler
import org.springframework.jndi.JndiObjectFactoryBean

class RabbitmqGrailsPlugin {
    // the plugin version
    def version = "0.3.6-ML"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.2 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/services/**",
            "grails-app/controllers/**",
            "grails-app/views/message/*",
            "grails-app/conf/Config.groovy",
            "src/groovy/org/grails/rabbitmq/test/**",
            "**/.gitignore"
    ]

    def author = "Jeff Brown"
    def authorEmail = "jeff.brown@springsource.com"
    def title = "RabbitMQ Plugin"
    def description = "The RabbitMQ plugin provides integration with the RabbitMQ Messaging System."

    def license = "APACHE"
    def organization = [ name: "SpringSource", url: "http://www.springsource.org/" ]
    def developers = [ [ name: "Peter Ledbrook", email: "pledbrook@vmware.com" ] ]
    def issueManagement = [ system: "JIRA", url: "http://jira.codehaus.org/browse/GRAILSPLUGINS" ]
    def scm = [ url: "https://github.com/grails-plugins/grails-rabbitmq" ]

    // URL to the plugin's documentation
    def documentation = "http://grails-plugins.github.com/grails-rabbitmq/"
    
    def loadAfter = ['services']
    def observe = ['*']

    private static LISTENER_CONTAINER_SUFFIX = '_MessageListenerContainer'

    def doWithSpring = { 

        def rabbitmqConfig = application.config.rabbitmq
        def configHolder = new RabbitConfigurationHolder(rabbitmqConfig)

        def connectionFactoryConfig = rabbitmqConfig?.connectionfactory
        
        def connectionFactoryJndiName = connectionFactoryConfig?.jndiName
        def connectionFactoryUsername = connectionFactoryConfig?.username
        def connectionFactoryPassword = connectionFactoryConfig?.password
        def connectionFactoryVirtualHost = connectionFactoryConfig?.virtualHost
        def connectionFactoryHostname = connectionFactoryConfig?.hostname
        def connectionChannelCacheSize = connectionFactoryConfig?.channelCacheSize ?: 10

		def messageConverterBean = rabbitmqConfig.messageConverterBean

		if(!connectionFactoryJndiName && (!connectionFactoryUsername || !connectionFactoryPassword || !connectionFactoryHostname)) {
        } else {
          
            log.debug "Connecting to rabbitmq ${connectionFactoryUsername}@${connectionFactoryHostname} with ${configHolder.getDefaultConcurrentConsumers()} consumers."
          
            def connectionFactoryClassName = connectionFactoryConfig?.className ?: 'org.springframework.amqp.rabbit.connection.CachingConnectionFactory'
            def parentClassLoader = getClass().classLoader
            def loader = new GroovyClassLoader(parentClassLoader)
            def connectionFactoryClass = loader.loadClass(connectionFactoryClassName)
            if(!connectionFactoryJndiName){
              rabbitMQConnectionFactory(connectionFactoryClass, connectionFactoryHostname) {
                  username = connectionFactoryUsername
                  password = connectionFactoryPassword
                  channelCacheSize = connectionChannelCacheSize

                  if (connectionFactoryVirtualHost) {
                      virtualHost = connectionFactoryVirtualHost
                  }
              }
            }else{
              underlyingConnectionFactory(JndiObjectFactoryBean){
                jndiName=connectionFactoryJndiName
              }
              rabbitMQConnectionFactory(connectionFactoryClass, underlyingConnectionFactory)
            }
            rabbitTemplate(RabbitTemplate) {
                connectionFactory = rabbitMQConnectionFactory
                if (messageConverterBean) messageConverter = ref(messageConverterBean)
            }
            adm(RabbitAdmin, rabbitMQConnectionFactory)
			
            dynamicRabbitConsumerService(DynamicRabbitConsumerService) {
                rabbitConfigurationHolder = configHolder
                rabbitMQConnectionFactory = rabbitMQConnectionFactory
            }

            Set registeredServices = new HashSet()
            application.serviceClasses.each { service ->
                def serviceClass = service.clazz
                def propertyName = service.propertyName

                def rabbitQueue = configHolder.getServiceQueueName(service)
                if(rabbitQueue) {
                    if(configHolder.isServiceEnabled(service)) {
                        def serviceConcurrentConsumers = configHolder.getServiceConcurrentConsumers(service)
                        log.info("Setting up rabbitmq listener for ${service.clazz} with ${serviceConcurrentConsumers} consumer(s)")
                        if(!registeredServices.add(propertyName)){
                            throw new IllegalArgumentException("Unable to initialize rabbitmq listeners properly. More than one service named ${propertyName}.")
                        }

                        "${propertyName}${LISTENER_CONTAINER_SUFFIX}"(SimpleMessageListenerContainer) {
                            // We manually start the listener once we have attached the
                            // service in doWithApplicationContext.
                            autoStartup = false
                            channelTransacted = configHolder.isServiceTransactional(service)
                            connectionFactory = rabbitMQConnectionFactory
                            concurrentConsumers = serviceConcurrentConsumers
                            queueNames = rabbitQueue

                        }
                    } else {
                        log.info("Not listening to ${service.clazz} it is disabled in configuration")
                    }
                }
                else {
                    def rabbitSubscribe = GCU.getStaticPropertyValue(serviceClass, 'rabbitSubscribe')
                    if (rabbitSubscribe) {
                        if (!(rabbitSubscribe instanceof CharSequence) && !(rabbitSubscribe instanceof Map)) {
                            log.error "The 'rabbitSubscribe' property on service ${service.fullName} must be a string or a map"
                        }
                        else {
                            if(configHolder.isServiceEnabled(service)) {
                                def serviceConcurrentConsumers = configHolder.getServiceConcurrentConsumers(service)
                                log.info("Setting up rabbitmq listener for ${service.clazz} with ${serviceConcurrentConsumers} consumer(s)")
                                if(!registeredServices.add(propertyName)){
                                    throw new IllegalArgumentException("Unable to initialize rabbitmq listeners properly. More than one service named ${propertyName}.")
                                }
                                "${propertyName}${LISTENER_CONTAINER_SUFFIX}"(AutoQueueMessageListenerContainer) {
                                    // We manually start the listener once we have attached the
                                    // service in doWithApplicationContext.
                                    autoStartup = false
                                    channelTransacted = configHolder.isServiceTransactional(service)
                                    connectionFactory = rabbitMQConnectionFactory
                                    concurrentConsumers = serviceConcurrentConsumers
                                    if (rabbitSubscribe instanceof Map) {
                                        exchangeBeanName = "grails.rabbit.exchange.${rabbitSubscribe.name}"
                                        routingKey = rabbitSubscribe.routingKey ?: '#'
                                    }
                                    else {
                                        exchangeBeanName = "grails.rabbit.exchange.${rabbitSubscribe}"
                                    }
                                }
                            } else {
                                log.info("Not listening to ${service.clazz} it is disabled in configuration")
                            }
                        }
                    }
                }
            }
            
            def queuesConfig = application.config.rabbitmq?.queues
            if(queuesConfig) {
                def queueBuilder = new RabbitQueueBuilder()
                queuesConfig = queuesConfig.clone()
                queuesConfig.delegate = queueBuilder
                queuesConfig.resolveStrategy = Closure.DELEGATE_FIRST
                queuesConfig()
                
                // Deal with declared exchanges first.
                queueBuilder.exchanges?.each { exchange ->
                    if (log.debugEnabled) {
                        log.debug "Registering exchange '${exchange.name}'"
                    }

                    "grails.rabbit.exchange.${exchange.name}"(exchange.type, exchange.name,
                            Boolean.valueOf(exchange.durable),
                            Boolean.valueOf(exchange.autoDelete),
                            exchange.arguments)
                }
                
                // Next, the queues.
                queueBuilder.queues?.each { queue ->
                    if (log.debugEnabled) {
                        log.debug "Registering queue '${queue.name}'"
                    }

                    "grails.rabbit.queue.${queue.name}"(Queue, queue.name,
                            Boolean.valueOf(queue.durable),
                            Boolean.valueOf(queue.exclusive),
                            Boolean.valueOf(queue.autoDelete),
                            queue.arguments,
                    )
                }
                
                // Finally, the bindings between exchanges and queues.
                queueBuilder.bindings?.each { binding ->
                    if (log.debugEnabled) {
                        log.debug "Registering binding between exchange '${binding.exchange}' & queue '${binding.queue}'"
                    }
                    
                    def args = [ ref("grails.rabbit.exchange.${binding.exchange}"), ref ("grails.rabbit.queue.${binding.queue}") ]
                    if (binding.rule) {
                        log.debug "Binding with rule '${binding.rule}'"

                        // Support GString and String for the rule. Other types of rule (Map
                        // is the only valid option atm) are passed through as is.
                        args << (binding.rule instanceof CharSequence ? binding.rule.toString() : binding.rule)
                    }

                    "grails.rabbit.binding.${binding.exchange}.${binding.queue}"(Binding, binding.queue, QUEUE, binding.exchange, binding.rule, binding.arguments )
                }
            }
        }   
    }
    
    def doWithDynamicMethods = { appCtx ->
        addDynamicMessageSendingMethods application.allClasses, appCtx
    }
    
    private addDynamicMessageSendingMethods(classes, ctx) {
        if(ctx.rabbitMQConnectionFactory) {
            classes.each { clz ->
                RabbitDynamicMethods.applyAllMethods(clz, ctx)
            }
        }
    }

    def doWithApplicationContext = { applicationContext ->
        def rabbitTemplate = applicationContext.getBean('rabbitTemplate', RabbitTemplate.class)
        def containerBeans = applicationContext.getBeansOfType(SimpleMessageListenerContainer)
        containerBeans.each { beanName, bean ->
            if(beanName.endsWith(LISTENER_CONTAINER_SUFFIX)) {
                def adapter = new MessageListenerAdapter()
                def serviceName = beanName - LISTENER_CONTAINER_SUFFIX

				def service = applicationContext.getBean(serviceName)
                adapter.delegate = service

                // If service defines explicit a message converter this will be used
				if (adapter.delegate.metaClass.hasProperty(service, 'messageConverter')) {
					def loader = new GroovyClassLoader(getClass().classLoader)
					MessageConverter messageConverterObj = null
					if (service.messageConverter) {
						def MessageConverterClass = loader.loadClass(service.messageConverter)
						messageConverterObj = (MessageConverter) MessageConverterClass.newInstance()
					}
					adapter.messageConverter = messageConverterObj
				// If service responds to raw message no message converter will be set
				} else if(service.respondsTo('handleMessage', Message)) {
                    adapter.messageConverter = null
				// Else we use the SimpleMessageConverter or what ever is configurated	
                } else {
					adapter.messageConverter = rabbitTemplate.messageConverter
                }

                bean.messageListener = adapter
                bean.errorHandler = new DefaultErrorHandler(serviceName)
                
                // Now that the listener is properly configured, we can start it.
                bean.start()
            }
        }
    }
    
    def onChange = { evt ->
        if(evt.source instanceof Class) {
            addDynamicMessageSendingMethods ([evt.source], evt.ctx)
        }
    }
}
