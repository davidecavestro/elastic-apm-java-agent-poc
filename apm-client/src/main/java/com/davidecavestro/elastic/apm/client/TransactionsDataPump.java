package com.davidecavestro.elastic.apm.client;

import com.davidecavestro.elastic.apm.client.model.transactions.ApmPayload;
import com.davidecavestro.elastic.apm.client.model.transactions.ApmTransaction;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
  protected void sendData (final ApmAgentContext apmAgentContext, final List<ApmTransaction> data){
    logger.info ("Sending transactions data");
      apmAgentContext.getApmApiService ().sendTransactions (createTransactionsPayload ()
          .withTransactions (data)
          .withApp (apmAgentContext.getApp ())
          .withSystem (apmAgentContext.getSystem ()));
  }

  protected ApmPayload createTransactionsPayload () {
    return new ApmPayload ();
  }

}
