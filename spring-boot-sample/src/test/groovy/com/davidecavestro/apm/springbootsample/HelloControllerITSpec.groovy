
package com.davidecavestro.apm.springbootsample

import com.davidecavestro.elastic.apm.client.ApmAgent
import com.davidecavestro.elastic.apm.client.ApmTracer
import com.davidecavestro.elastic.apm.spring.webmvc.ApmSpringService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import spock.lang.Specification
import spock.mock.DetachedMockFactory

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests for ensuring compatibility with Spring-Boot's {@link WebMvcTest} annotation.
 */
//@WebMvcTest
@SpringBootTest(classes=SpringBootSampleApplication)
@AutoConfigureMockMvc
class HelloControllerITSpec extends Specification {

  @Autowired
  MockMvc mvc

  @Autowired
  HelloController helloController

  @Autowired
  private ApmSpringService apmSpringService

  def 'http requests generate apm transactions'() {
    given: 'an index controller'
    helloController.index(_) >> 'test message'

    when: 'the index url is called'
    def response = mvc.perform(MockMvcRequestBuilders.get('/'))

    then: 'a transaction is traced'
    with(apmSpringService) {
      1 * traceTransaction(*_) >> {arguments ->
        println arguments
      }
    }
    response.andExpect(status().isOk())
        .andExpect(content().string('test message'))
  }

  @TestConfiguration
  static class MockConfig {
    def detachedMockFactory = new DetachedMockFactory()

    @Bean
    HelloController helloController() {
      return detachedMockFactory.Stub(HelloController)
    }

    @Bean
    ApmSpringService apmSpringService() {
      return detachedMockFactory.Spy(ApmSpringService)
    }
  }
}