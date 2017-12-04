package com.davidecavestro.elastic.apm.spring.webmvc;

import com.davidecavestro.elastic.apm.client.ApmAgent;
import com.davidecavestro.elastic.apm.client.model.errors.ApmError;
import com.davidecavestro.elastic.apm.client.model.transactions.ApmTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ApmSpringService{
  @Autowired
  private ApmAgent apmAgent;

  @EventListener
  public void onApplicationEvent(final ContextRefreshedEvent event) {
    apmAgent.refresh ();
  }

  public ApmError prepareError (
      final Exception ex,
      final HttpServletRequest request,
      final HttpServletResponse response) throws InterruptedException {
    return apmAgent.prepareError (ex, request, response);
  }

  public void traceError (final ApmError error) throws InterruptedException {
    apmAgent.traceError (error);
  }

  public void traceError (
      final Exception ex,
      final HttpServletRequest request,
      final HttpServletResponse response) throws InterruptedException {
    traceError (prepareError (ex, request, response));
  }

  public ApmTransaction prepareTransaction (
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String status,
      final long durationNanos) {
    return apmAgent.prepareTransaction (request, response, status, durationNanos);
  }

  public void traceTransaction (final ApmTransaction apmTransaction) throws InterruptedException {
    apmAgent.traceTransaction (apmTransaction);
  }

  public void traceTransaction (
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String status,
      final long durationNanos) throws InterruptedException {

    traceTransaction (prepareTransaction (request, response, status, durationNanos));
  }

}
