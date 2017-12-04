package com.davidecavestro.elastic.apm.client;

import com.davidecavestro.elastic.apm.client.model.errors.ApmError;
import com.davidecavestro.elastic.apm.client.model.errors.ApmPayload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Pumps errors to the APM server.
 */
public class ErrorsDataPump extends AbstractDataPump<ApmError> {
  private static final Log logger = LogFactory.getLog(ApmAgent.class);

  public ErrorsDataPump (final ApmAgentContext apmAgentContext, final ArrayBlockingQueue<ApmError> errorsQueue) {
    super (apmAgentContext, errorsQueue);
  }

  @Override
  protected void sendData (final ApmAgentContext apmAgentContext, final List<ApmError> data) throws IOException {
    logger.info ("Sending error data");
    final Response<Void> response = apmAgentContext.getApmApiService ().sendErrors (createErrorsPayload ()
        .withErrors (data)
        .withApp (apmAgentContext.getApp ())
        .withSystem (apmAgentContext.getSystem ())).execute ();
    if (!response.isSuccessful ()) {//FIXME handle errors and retry
      logger.warn ("Failed sending date "+ response);
    }
  }

  protected ApmPayload createErrorsPayload () {
    return new ApmPayload ();
  }
}
