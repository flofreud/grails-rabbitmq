grails.doc.authors = 'Jeff Brown, Peter Ledbrook'
grails.doc.license = 'Apache License 2.0'
grails.doc.title = 'RabbitMQ Plugin'
grails.views.default.codec="none" // none, html, base64
grails.views.gsp.encoding="UTF-8"

environments {
    test {
        rabbitmq {
            connectionfactory {
                username = 'guest'
                password = 'guest'
                hostname = 'localhost'
            }
        }
    }
	development {
		rabbitmq {
			connectionfactory {
				username = 'guest'
				password = 'guest'
				hostname = 'localhost'
			}
		}
	}
}

log4j = {
	root {
		warn 'stdout'
	}
}