package com.davidecavestro.elastic.apm.client;

import com.davidecavestro.elastic.apm.client.model.transactions.ApmPayload;
import com.davidecavestro.elastic.apm.client.model.transactions.ApmTransaction;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Pumps transactions to the APM server.
 */
public class TransactionsDataPump extends AbstractDataPump<ApmTransaction> {
  private static final Log logger = LogFactory.getLog(ApmAgent.class);

  public TransactionsDataPump (final ApmAgentContext apmAgentContext, final BlockingQueue<ApmTransaction> queue) {
    super (apmAgentContext, queue);
  }


  @Override
  protected void sendData (final ApmAgentContext apmAgentContext, final List<ApmTransaction> data) throws IOException {
    logger.info ("Sending transactions data");
    final Response<Void> response = apmAgentContext.getApmApiService ().sendTransactions (createTransactionsPayload ()
        .withTransactions (data)
        .withApp (apmAgentContext.getApp ())
        .withSystem (apmAgentContext.getSystem ())).execute ();
    if (!response.isSuccessful ()) {//FIXME handle errors and retry
      logger.warn ("Failed sending date "+ response);
    }
  }

  protected ApmPayload createTransactionsPayload () {
    return new ApmPayload ();
  }

}
