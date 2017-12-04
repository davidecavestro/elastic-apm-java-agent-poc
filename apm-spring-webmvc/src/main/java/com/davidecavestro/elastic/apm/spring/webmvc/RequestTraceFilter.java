package com.davidecavestro.elastic.apm.spring.webmvc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

  //TODO consider witching to a different logging library
  private static final Log logger = LogFactory.getLog(RequestTraceFilter.class);

  @Autowired
  private ApmSpringService apmSpringService;

  @Override
  protected void doFilterInternal (final HttpServletRequest request, final HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    final long startTime = System.nanoTime ();
    int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
    try {
      filterChain.doFilter(request, response);
      status = response.getStatus();
    } finally {
      try {
        apmSpringService.traceTransaction (request, response, Integer.toString (status), System.nanoTime () - startTime);
      } catch (final InterruptedException e) {
        logger.warn (String.format ("Failed to trace transaction for request %s", request.getRequestURI ()), e);
      }
    }
  }
}
