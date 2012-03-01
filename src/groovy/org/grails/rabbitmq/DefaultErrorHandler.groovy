package org.grails.rabbitmq

import org.slf4j.LoggerFactory
import org.springframework.util.ErrorHandler

/**
 * Default listener's error handler
 */
class DefaultErrorHandler implements ErrorHandler {
    private static final log = LoggerFactory.getLogger(DefaultErrorHandler)
    String listenerName

    DefaultErrorHandler(String listenerName) {
        this.listenerName = listenerName
    }


    void handleError(Throwable throwable) {
        log.error("An error was thrown in listener $listenerName", throwable)
    }
}
