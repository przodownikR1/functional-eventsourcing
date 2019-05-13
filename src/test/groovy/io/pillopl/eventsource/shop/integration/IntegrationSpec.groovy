package io.pillopl.eventsource.shop.integration

import groovy.transform.CompileStatic
import io.pillopl.eventsource.shop.Application
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import spock.lang.Specification

@ContextConfiguration(classes = [Application])
@CompileStatic
@WebAppConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ActiveProfiles("test")
class IntegrationSpec extends Specification {
}
