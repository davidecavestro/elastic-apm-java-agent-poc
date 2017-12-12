package com.davidecavestro.elastic.apm.client.api;

import com.davidecavestro.elastic.apm.client.model.ApmApp;
import com.davidecavestro.elastic.apm.client.model.errors.ApmSystem;
import com.davidecavestro.elastic.apm.client.retrofit.RetrofitApmApiService;

/**
 * Execution context for the APM agent.
 */
public interface ApmAgentContext {

  ApmApp getApp();

  ApmSystem getSystem ();

  RetrofitApmApiService getApmApiService();

  int getPumpBatchSize ();

}
