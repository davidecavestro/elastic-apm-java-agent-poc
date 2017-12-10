package com.davidecavestro.elastic.apm.client;

import com.davidecavestro.elastic.apm.client.model.errors.ApmError;
import com.davidecavestro.elastic.apm.client.model.transactions.ApmTransaction;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface ApmTracer {
  /**
   * Sends transaction tracing data, possibly waiting for for space to
   * become available.
   *
   * @param ex the exception to trace
   * @throws InterruptedException
   */
  void traceError (Exception ex,
                   HttpServletRequest request,
                   HttpServletResponse response) throws InterruptedException;

  /**
   * Sends error tracing data, possibly waiting for for space to
   * become available.
   *
   * @param error the error representation
   * @throws InterruptedException
   */
  void traceError (ApmError error) throws InterruptedException;

  /**
   * Returns a tracing error representation filled with the passed exception.
   * @param ex the exception to trace
   * @return the tracing error representation filled with the passed exception
   */
  ApmError prepareError (
      Exception ex,
      HttpServletRequest request,
      HttpServletResponse response);

  /**
   * Sends transaction tracing data, possibly waiting for for space to
   * become available.
   * @param transaction the transaction representation

   * @throws InterruptedException if interrupted while waiting for space to
   * become available for storing the transaction
   */
  void traceTransaction (ApmTransaction transaction) throws InterruptedException;

  void traceTransaction (
      HttpServletRequest request,
      HttpServletResponse response,
      String status,
      long durationNanos) throws InterruptedException;

  /**
   * Returns a transaction representation filled with values obtained from passed args
   *
   * @param request the http request
   * @param response the http response
   * @param status the transaction status
   * @param durationNanos transaction duration in nanos
   * @return a partially configured transaction representation
   */
  ApmTransaction prepareTransaction (
      HttpServletRequest request,
      HttpServletResponse response,
      String status,
      long durationNanos);
}
