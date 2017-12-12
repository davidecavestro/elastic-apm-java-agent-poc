package com.davidecavestro.elastic.apm.client.api;

import com.davidecavestro.elastic.apm.client.api.ApmAgentContext;

import java.io.IOException;
import java.util.List;

/**
 * Publish data to the APM server.
 *
 * @param <T> type of data to publish
 */
public interface ApmPublisher<T> {
  /**
   * Sends a batch of data to the APM server.
   *
   * @param apmAgentContext the APM agent context
   * @param data the data to publish
   */
  void sendData (final ApmAgentContext apmAgentContext, final List<T> data) throws IOException;

}
