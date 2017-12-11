package com.davidecavestro.elastic.apm.client;

import com.davidecavestro.elastic.apm.client.model.*;
import com.davidecavestro.elastic.apm.client.model.errors.*;
import com.davidecavestro.elastic.apm.client.model.transactions.ApmTransaction;
import com.davidecavestro.elastic.apm.client.retrofit.RetrofitApmApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ApmAgent implements ApmAgentContext, ApmTracer {

  private static final Log logger = LogFactory.getLog(ApmAgent.class);

  private String appName;
  private String secretToken;
  private String appVersion = "1.0";//TODO expose setters/config

  private String apmHost = "http://localhost:8200";

  private long initialDelay = 1000L;
  private long period = 1000L;

  private int queueCapacity;
  private boolean fairQueue;
  private int publishBatchSize;
  private long enqueueTimeout;

  private ApmApp app = new ApmApp ();
  private ApmSystem system = new ApmSystem ();

  private ArrayBlockingQueue<ApmError> errorsQueue;
  private ArrayBlockingQueue<ApmTransaction> transactionsQueue;

  private ScheduledExecutorService errorsSender;
  private ScheduledExecutorService transactionsSender;
  private RetrofitApmApiService apmApiService;

  public ApmAgent () {
    //no-op
  }

  public void refresh () {
//    stop();//TODO store activation status
    start ();
  }

  public ApmAgent start () {
    logger.info ("Starting ES APM agent");
    initApmInfo();
    apmApiService = createApiClient (RetrofitApmApiService.class);

    errorsQueue = new ArrayBlockingQueue<> (getQueueCapacity (), fairQueue);
    transactionsQueue = new ArrayBlockingQueue<> (getQueueCapacity (), fairQueue);

    // init senders
    errorsSender = startQueue (new ErrorsDataPump (this, errorsQueue));
    transactionsSender = startQueue (new TransactionsDataPump (this, transactionsQueue));

    return this;
 }

  protected void initApmInfo () {
    getApp()
        .withName (appName)
        .withAgent (new com.davidecavestro.elastic.apm.client.model.ApmAgent ()
            .withName ("java")
            .withVersion ("0.1.0"))//FIXME take from props
        .withLanguage (new ApmLanguage ()
            .withName ("java")
            .withVersion (System.getProperty("java.version")))
        .withVersion (appVersion)
        .withRuntime (new ApmRuntime ().withName (System.getProperty("java.vm.name")).withVersion (System.getProperty("java.vm.version")));
    system
        .withArchitecture (System.getProperty("os.arch"))
        .withPlatform (System.getProperty("os.arch"));


    try {
      system.withHostname (InetAddress.getLocalHost().getHostName());
    } catch (final UnknownHostException e) {
      //safe to catch here
      logger.warn ("Cannot determine host name", e);
    }
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
    final HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
    logging.setLevel(HttpLoggingInterceptor.Level.BODY);

    final ObjectMapper mapper = new ObjectMapper ();
    final JacksonConverterFactory jacksonConverterFactory = JacksonConverterFactory.create (mapper);
    final OkHttpClient httpClient = new OkHttpClient.Builder()
        .addNetworkInterceptor (new FixedContentTypeInterceptor ())
        .addInterceptor (logging).build ();

    final Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(getApmHost ())
        .addConverterFactory (jacksonConverterFactory)
        .client (httpClient)
        .build();

    return retrofit.create(type);
  }

  @Override
  public void traceError (final Exception ex,
                          final HttpServletRequest request,
                          final HttpServletResponse response) throws InterruptedException {
    traceError (prepareError (ex, request, response));
  }

  @Override
  public void traceError (final ApmError error) throws InterruptedException {
    errorsQueue.offer (error, enqueueTimeout, TimeUnit.MILLISECONDS);
  }

  @Override
  public ApmError prepareError (
      final Exception ex,
      final HttpServletRequest request,
      final HttpServletResponse response) {

    final String id = generateUUID ();
    //TODO check if missing data
    final Throwable rootCause = ExceptionUtils.getRootCause (ex);
    final String rootCauseMessage = ExceptionUtils.getRootCauseMessage (ex);

    final String[] rootCauseStackTrace = ExceptionUtils.getRootCauseStackTrace (ex);
    final String culprit = rootCauseStackTrace.length > 0 ?
        rootCauseStackTrace[0] : null;

    final List<ApmStacktrace> stackTrace = new ArrayList<> ();

    for (final StackTraceElement traceElement : ex.getStackTrace ()) {
      final ApmStacktrace trace = new ApmStacktrace ()
//          .withLineno (Integer.valueOf (traceElement.getLineNumber ()).doubleValue ())
          .withAdditionalProperty ("lineno", traceElement.getLineNumber ())
          .withFilename (traceElement.getFileName ())
          .withFunction (traceElement.getMethodName ())
          .withModule (traceElement.getClassName ());
      stackTrace.add (trace);
    }

    return new ApmError ()
        .withId (id)
//        .withTimestamp (LocalDateTime.now ()) //TODO check why it isn't automatically converted to string
        .withAdditionalProperty ("timestamp", getSimpleDateFormat ().format (new Date ()))
        .withCulprit (culprit)
        .withContext (toApmContext (request, response))
        .withException (new ApmException ().withStacktrace (stackTrace).withMessage (rootCauseMessage))
        //.withLog (new ApmLog ().wi)//TODO add log
        ;
  }

  protected ApmContext toApmContext (final HttpServletRequest request, final HttpServletResponse response) {
    return new ApmContext ()
        .withRequest (toApmRequest(request))
        .withResponse (toApmResponse (response));
  }

  protected ApmResponse toApmResponse (final HttpServletResponse response) {
    final ApmResponse apmResponse;
    if (response != null) {
      final ApmHeaders apmHeaders = new ApmHeaders ()
          .withContent_type (response.getContentType ());
      for (final String headerName : response.getHeaderNames ()) {
        apmHeaders.withAdditionalProperty (headerName, response.getHeader (headerName));
      }

      apmResponse = new ApmResponse ()
          .withHeaders (apmHeaders)
          .withStatus_code ((double) response.getStatus ())
        //TODO add missing props
      ;
    } else {
      apmResponse = null;
    }
    return apmResponse;
  }

  protected ApmRequest toApmRequest (final HttpServletRequest request) {
    final ApmRequest apmRequest;
    if (request != null) {
      final ApmHeaders_ apmHeaders = new ApmHeaders_ ();
      apmHeaders.withContent_type (request.getContentType ());
      for (
          final Enumeration<String> headerNames = request.getHeaderNames ();
          headerNames.hasMoreElements (); ) {
        final String headerName = headerNames.nextElement ();
        apmHeaders.withAdditionalProperty (headerName, request.getHeader (headerName));
      }

      apmRequest = new ApmRequest ().withUrl (new ApmUrl ()
          .withHostname (request.getServerName ())
          .withPort (toString (request.getServerPort ()))
          .withPathname (request.getRequestURI ())
          .withSearch (request.getQueryString ())
          .withProtocol (request.getScheme ()))
          .withMethod (request.getMethod ())
          .withHeaders (apmHeaders)
          .withEnv (new ApmEnv ());
    } else {
      apmRequest = null;
    }
    return apmRequest;
  }

  protected String toString (final int intValue) {
    return Integer.toString (intValue);
  }

  @Override
  public void traceTransaction (final ApmTransaction transaction) throws InterruptedException {
    transactionsQueue.offer (transaction, enqueueTimeout, TimeUnit.MILLISECONDS);
  }

  @Override
  public void traceTransaction (
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String status,
      final long durationNanos) throws InterruptedException {
    traceTransaction (prepareTransaction (request, response, status, durationNanos));
  }

  @Override
  public ApmTransaction prepareTransaction (
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String status,
      final long durationNanos) {
    final String id = generateUUID ();
    final String name = String.format ("%s %s", request.getMethod (), request.getRequestURI ());
    final SimpleDateFormat dateFormat = getSimpleDateFormat ();
    final ApmTransaction transaction = new ApmTransaction ();
    transaction
        .withId (id)
        .withType ("request")
        .withName (name)
        .withResult (status)
        .withDuration (Double.valueOf ((double)durationNanos/1000000))
//        .withTimestamp (LocalDateTime.now ()) //TODO check why it isn't automatically converted to string
        .withAdditionalProperty ("timestamp", dateFormat.format (new Date()))
    .withContext (toApmContext (request, response))
    ;//TODO add traces

    return transaction;
  }

  protected SimpleDateFormat getSimpleDateFormat () {
    final SimpleDateFormat dateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    dateFormat.setTimeZone (TimeZone.getTimeZone ("UTC"));
    return dateFormat;
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

  public String getAppVersion () {
    return appVersion;
  }

  public void setAppVersion (String appVersion) {
    this.appVersion = appVersion;
  }

  public ArrayBlockingQueue<ApmError> getErrorsQueue () {
    return errorsQueue;
  }

  public void setErrorsQueue (ArrayBlockingQueue<ApmError> errorsQueue) {
    this.errorsQueue = errorsQueue;
  }

  public ArrayBlockingQueue<ApmTransaction> getTransactionsQueue () {
    return transactionsQueue;
  }

  public void setTransactionsQueue (ArrayBlockingQueue<ApmTransaction> transactionsQueue) {
    this.transactionsQueue = transactionsQueue;
  }

  public ScheduledExecutorService getErrorsSender () {
    return errorsSender;
  }

  public void setErrorsSender (ScheduledExecutorService errorsSender) {
    this.errorsSender = errorsSender;
  }

  public ScheduledExecutorService getTransactionsSender () {
    return transactionsSender;
  }

  public void setTransactionsSender (ScheduledExecutorService transactionsSender) {
    this.transactionsSender = transactionsSender;
  }

  public void setApmApiService (RetrofitApmApiService apmApiService) {
    this.apmApiService = apmApiService;
  }

  public ApmAgent withAppName (String val) {
    appName = val;
    return this;
  }

  public ApmAgent withSecretToken (String val) {
    secretToken = val;
    return this;
  }

  public ApmAgent withAppVersion (String val) {
    appVersion = val;
    return this;
  }

  public ApmAgent withApmHost (String val) {
    apmHost = val;
    return this;
  }

  public ApmAgent withInitialDelay (long val) {
    initialDelay = val;
    return this;
  }

  public ApmAgent withPeriod (long val) {
    period = val;
    return this;
  }

  public ApmAgent withQueueCapacity (int val) {
    queueCapacity = val;
    return this;
  }

  public ApmAgent withFairQueue (boolean val) {
    fairQueue = val;
    return this;
  }

  public ApmAgent withPublishBatchSize (int val) {
    publishBatchSize = val;
    return this;
  }

  public ApmAgent withEnqueueTimeout (long val) {
    enqueueTimeout = val;
    return this;
  }

  public ApmAgent withErrorsQueue (ArrayBlockingQueue<ApmError> val) {
    errorsQueue = val;
    return this;
  }

  public ApmAgent withTransactionsQueue (ArrayBlockingQueue<ApmTransaction> val) {
    transactionsQueue = val;
    return this;
  }

  public ApmAgent withErrorsSender (ScheduledExecutorService val) {
    errorsSender = val;
    return this;
  }

  public ApmAgent withTransactionsSender (ScheduledExecutorService val) {
    transactionsSender = val;
    return this;
  }

  public ApmAgent withApmApiService (RetrofitApmApiService val) {
    apmApiService = val;
    return this;
  }

  private static class FixedContentTypeInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
      final Request.Builder requestBuilder = chain.request().newBuilder();
      //force content type without charset to avoid getting 'Decoding error: invalid content type: application/json; charset=UTF-8'
      requestBuilder.header("Content-Type", "application/json");
      return chain.proceed(requestBuilder.build());
    }
  }

}
