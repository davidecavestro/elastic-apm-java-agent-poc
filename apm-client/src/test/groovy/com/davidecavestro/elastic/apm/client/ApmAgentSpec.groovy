package com.davidecavestro.elastic.apm.client

import com.davidecavestro.elastic.apm.client.model.transactions.ApmTransaction
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ApmAgentSpec extends Specification {

  ApmAgent apmAgent = new ApmAgent()
      .withAppName ('testApp')
      .withSecretToken ('mySecret')
      .withAppVersion ('v0')
      .withApmHost ('http://localhost')
      .withInitialDelay (0)
      .withPeriod (1000)
      .withQueueCapacity (10)
      .withFairQueue (true)
      .withPublishBatchSize (10)
      .withEnqueueTimeout (1000)
//        .withErrorsQueue (errorsQueue)
//        .withTransactionsQueue (transactionsQueue)
//        .withErrorsSender (errorsSender)
//        .withTransactionsSender (transactionsSender)
//        .withApmApiService (apmApiService)

  HttpServletRequest request = Mock()
  HttpServletResponse response = Mock()

  def 'apm agent feed transactions queue'() {
    given: 'a freshly initialized apm agent'
    apmAgent.start()
    apmAgent.transactionsQueue.empty
    with (response) {response->
      response.getHeaderNames() >> ['Content-type']
      response.getHeader('Content-type') >> 'text/plain'
    }
    with (request) {request->
      request.getHeaderNames() >> Collections.enumeration(['Accept'])
      request.getHeader('Accept') >> 'text/plain'
    }
    with (request) {request->
      request.getRequestURI() >> '/'
      request.getMethod() >> 'GET'
    }

    when: 'a transaction is traced'
    apmAgent.traceTransaction(request, response, '200', 1500)

    then: 'the transaction queue receives the object representation'

    apmAgent.transactionsQueue.size()==1
    with (apmAgent.transactionsQueue[0]) { ApmTransaction transaction ->
      transaction.result == '200'
      transaction.context.request.url.pathname == '/'
    }
  }


}
