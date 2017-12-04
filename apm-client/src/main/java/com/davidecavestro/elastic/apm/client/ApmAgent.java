package com.davidecavestro.elastic.apm.client;

import com.davidecavestro.elastic.apm.client.model.*;
import com.davidecavestro.elastic.apm.client.model.errors.ApmError;
import com.davidecavestro.elastic.apm.client.model.errors.ApmException;
import com.davidecavestro.elastic.apm.client.model.errors.ApmStacktrace;
import com.davidecavestro.elastic.apm.client.model.errors.ApmSystem;
import com.davidecavestro.elastic.apm.client.model.transactions.ApmTransaction;
import com.davidecavestro.elastic.apm.client.retrofit.RetrofitApmApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ApmAgent implements ApmAgentContext {

  private static final Log logger = LogFactory.getLog(ApmAgent.class);

  private String appName;
  private String secretToken;

  private String apmHost = "http://localhost:8200";

  private long initialDelay = 1000L;
  private long period = 1000L;

  private int queueCapacity;
  private boolean fairQueue;
  private int publishBatchSize;
  private long enqueueTimeout;

  private ApmApp app;
  private ApmSystem system;

  private ArrayBlockingQueue<ApmError> errorsQueue;
  private ArrayBlockingQueue<ApmTransaction> transactionsQueue;

  private ScheduledExecutorService errorsSender;
  private ScheduledExecutorService transactionsSender;
  private RetrofitApmApiService apmApiService;
  private String appVersion = "1.0";//TODO expose setters/config

  public void refresh () {
//    stop();//TODO store activation status
    start ();
  }

  public void start () {
    logger.info ("Starting ES APM agent");
    app = new ApmApp ()
        .withAdditionalProperty ("name", appName).withAdditionalProperty ("agent", new ApmAgent ())
        .withAdditionalProperty ("version", appVersion);

    apmApiService = createApiClient (RetrofitApmApiService.class);

    errorsQueue = new ArrayBlockingQueue<> (getQueueCapacity (), fairQueue);
    transactionsQueue = new ArrayBlockingQueue<> (getQueueCapacity (), fairQueue);

    // init senders
    errorsSender = startQueue (new ErrorsDataPump (this, errorsQueue));
    transactionsSender = startQueue (new TransactionsDataPump (this, transactionsQueue));
 }

  protected ScheduledExecutorService startQueue (final TimerTask task) {
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor ();
    executor.scheduleAtFixedRate (task, getInitialDelay (), getPeriod (), TimeUnit.MILLISECONDS);
    return executor;
  }

  public void stop () {
    if (errorsSender!=null ) {
      errorsSender.shutdown ();
    }
    if (transactionsSender!=null ) {
      transactionsSender.shutdown ();
    }
  }

  //TODO decouple from Retrofit
  protected <T> T createApiClient (Class<T> type) {
    final ObjectMapper mapper = new ObjectMapper ();
    final JacksonConverterFactory jacksonConverterFactory = JacksonConverterFactory.create (mapper);
    final Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(getApmHost ())
        .addConverterFactory (jacksonConverterFactory)
        .build();

    return retrofit.create(type);
  }

  /**
   * Sends transaction tracing data, possibly waiting for for space to
   * become available.
   *
   * @param ex the exception to trace
   * @throws InterruptedException
   */
  public void traceError (final Exception ex,
                          final HttpServletRequest request,
                          final HttpServletResponse response) throws InterruptedException {
    traceError (prepareError (ex, request, response));
  }

  /**
   * Sends error tracing data, possibly waiting for for space to
   * become available.
   *
   * @param error the error representation
   * @throws InterruptedException
   */
  public void traceError (final ApmError error) throws InterruptedException {
    errorsQueue.offer (error, enqueueTimeout, TimeUnit.MILLISECONDS);
  }

  /**
   * Returns a tracing error representation filled with the passed exception.
   * @param ex the exception to trace
   * @return the tracing error representation filled with the passed exception
   */
  public ApmError prepareError (
      final Exception ex,
      final HttpServletRequest request,
      final HttpServletResponse response) {
    final ApmError error = new ApmError ();
    final String id = generateUUID ();
    final Throwable rootCause = ExceptionUtils.getRootCause (ex);
    final String rootCauseMessage = ExceptionUtils.getRootCauseMessage (ex);
    final String[] rootCauseStackTrace = ExceptionUtils.getRootCauseStackTrace (ex);
    final String culprit = rootCauseStackTrace.length>0?
        rootCauseStackTrace[0]:null;

    final ApmContext errorContext = new ApmContext();
    if (request!=null) {
      final ApmHeaders_ apmHeaders = new ApmHeaders_();
      apmHeaders.withContent_type (response.getContentType ());
      for (
          final Enumeration<String> headerNames = request.getHeaderNames ();
          headerNames.hasMoreElements ();) {
        final String headerName = headerNames.nextElement ();
        apmHeaders.withAdditionalProperty (headerName, request.getHeader (headerName));
      }

      errorContext.withRequest (
          new ApmRequest ().withUrl (new ApmUrl ()
              .withHostname (request.getServerName ())
              .withPort (toString (request.getServerPort ()))
              .withPathname (request.getRequestURI ())
              .withSearch (request.getQueryString ())
              .withProtocol (request.getScheme ()))
          .withHeaders (apmHeaders)
          .withEnv (new ApmEnv ())
      );
    }
    if (response!=null) {
      final ApmResponse apmResponse = new ApmResponse ();
      final ApmHeaders apmHeaders = new ApmHeaders();
      apmHeaders.withContent_type (response.getContentType ());
      for (final String headerName : response.getHeaderNames ()) {
        apmHeaders.withAdditionalProperty (headerName, response.getHeader (headerName));
      }

      apmResponse.withHeaders (apmHeaders);

      errorContext.withResponse (apmResponse.withStatus_code ((double) response.getStatus ()));
    }

    error.withId (id);
    error.withTimestamp (LocalDateTime.now ());
    error.withCulprit (culprit);

    error.withContext (errorContext);
    final List<ApmStacktrace> stackTrace = new ArrayList<> ();

    for (final StackTraceElement traceElement : ex.getStackTrace ()) {
      final ApmStacktrace trace = new ApmStacktrace ();
      trace.withLineno (Integer.valueOf (traceElement.getLineNumber ()).doubleValue ());
      trace.withFilename (traceElement.getFileName ());
      trace.withFunction (traceElement.getMethodName ());
      trace.withModule (traceElement.getClassName ());
    }

    error.withException (new ApmException ().withStacktrace (stackTrace));
    return error;
  }

  protected String toString (final int intValue) {
    return Integer.toString (intValue);
  }

  /**
   * Sends transaction tracing data, possibly waiting for for space to
   * become available.
   * @param transaction the transaction representation

   * @throws InterruptedException if interrupted while waiting for space to
   * become available for storing the transaction
   */
  public void traceTransaction (final ApmTransaction transaction) throws InterruptedException {
    transactionsQueue.offer (transaction, enqueueTimeout, TimeUnit.MILLISECONDS);
  }

  /**
   * Returns a transaction representation filled with values obtained from passed args
   *
   * @param request the http request
   * @param response the http response
   * @param status the transaction status
   * @param durationNanos transaction duration in nanos
   * @return a partially configured transaction representation
   */
  public ApmTransaction prepareTransaction (
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String status,
      final long durationNanos) {
    final String id = generateUUID ();
    final String name = String.format ("%s %s", request.getMethod (), request.getRequestURI ());
    final ApmTransaction transaction = new ApmTransaction ();
    transaction
        .withId (id)
        .withName (name)
        .withResult (status)
        .withDuration (Double.valueOf ((double)durationNanos/1000000))
        .withTimestamp (LocalDateTime.now ());//TODO add traces

    return transaction;
  }

  private String generateUUID () {
    return UUID.randomUUID ().toString ();
  }

  @Override
  public ApmApp getApp () {
    return app;
  }

  @Override
  public ApmSystem getSystem () {
    return system;
  }

  @Override
  public RetrofitApmApiService getApmApiService () {
    return apmApiService;
  }

  @Override
  public int getPumpBatchSize () {
    return publishBatchSize;
  }

  /**
   * Set required app name (allowed characters: a-z, A-Z, 0-9, -, _, and space)
   */
  public String getAppName () {
    return appName;
  }

  public void setAppName (String appName) {
    this.appName = appName;
  }

  /**
   * Use if APM Server requires a token
   */
  public String getSecretToken () {
    return secretToken;
  }

  public void setSecretToken (String secretToken) {
    this.secretToken = secretToken;
  }

  /**
   * Set custom APM Server URL (default: http://localhost:8200)
   */
  public String getApmHost () {
    return apmHost;
  }

  public void setApmHost (String apmHost) {
    this.apmHost = apmHost;
  }

  public long getInitialDelay () {
    return initialDelay;
  }

  public void setInitialDelay (long initialDelay) {
    this.initialDelay = initialDelay;
  }

  public long getPeriod () {
    return period;
  }

  public void setPeriod (long period) {
    this.period = period;
  }

  public int getQueueCapacity () {
    return queueCapacity;
  }

  public void setQueueCapacity (int queueCapacity) {
    this.queueCapacity = queueCapacity;
  }

  public boolean isFairQueue () {
    return fairQueue;
  }

  public void setFairQueue (boolean fairQueue) {
    this.fairQueue = fairQueue;
  }

  public int getPublishBatchSize () {
    return publishBatchSize;
  }

  public void setPublishBatchSize (int publishBatchSize) {
    this.publishBatchSize = publishBatchSize;
  }

  public long getEnqueueTimeout () {
    return enqueueTimeout;
  }

  public void setEnqueueTimeout (long enqueueTimeout) {
    this.enqueueTimeout = enqueueTimeout;
  }
}
