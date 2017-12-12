package com.davidecavestro.elastic.apm.client;

import com.davidecavestro.elastic.apm.client.api.ApmAgentContext;
import com.davidecavestro.elastic.apm.client.api.ApmPublisher;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;

/**
 * A timer task that pumps data from a queue to the APM server.
 *
 * @param <T> type of queue content
 */
public abstract class AbstractDataPump<T> extends TimerTask implements ApmPublisher<T> {
  private static final Log logger = LogFactory.getLog(ApmAgent.class);

  private final ApmAgentContext apmAgentContext;
  private final BlockingQueue<T> queue;

  public AbstractDataPump (final ApmAgentContext apmAgentContext, final BlockingQueue<T> queue) {
    this.apmAgentContext = apmAgentContext;
    this.queue = queue;
  }

  public void run () {
    try {
      pumpData (apmAgentContext, queue);
    } catch (final Exception e) {
      logger.error ("Cannot send data", e);
    }
  }

  /**
   * Pumps data from the queue, within the agent context.
   *
   * @param apmAgentContext the agent context
   * @param queue           the data queue
   */
  protected void pumpData (ApmAgentContext apmAgentContext, BlockingQueue<T> queue) throws IOException {
    final List<T> data = new ArrayList<> ();
    queue.drainTo (data, apmAgentContext.getPumpBatchSize ());

    if (data.isEmpty ()) {
      logger.info ("No data to pump");
    } else {
      logger.info ("Pumping data");
      sendData (apmAgentContext, data);
    }
  }
}
