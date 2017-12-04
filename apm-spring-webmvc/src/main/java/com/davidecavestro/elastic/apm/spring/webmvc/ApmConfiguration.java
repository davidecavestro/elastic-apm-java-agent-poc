package com.davidecavestro.elastic.apm.spring.webmvc;

import com.davidecavestro.elastic.apm.client.ApmAgent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan (basePackageClasses = WebMvcPackageRef.class)
public class ApmConfiguration {

  @Value ("${elastic.apm.host:http://localhost:8200}")
  private String apmHost;
  @Value ("${elastic.apm.appname:MYAPP}")
  private String appName;
  @Value ("${elastic.apm.secrettoken:MYSECRET}")
  private String secretToken;
  @Value ("${elastic.apm.initialdelay:5000}")
  private long initialDelay;
  @Value ("${elastic.apm.period:1000}")
  private long period;
  @Value ("${elastic.apm.queue.capacity:10000}")
  private int queueCapacity;
  @Value ("${elastic.apm.queue.batchsize:100}")
  private int publishBatchSize;
  @Value ("${elastic.apm.queue.fair:true}")
  private boolean fairQueue;
  @Value ("${elastic.apm.queue.enqueuetimeout:5000}")
  private long enqueueTimeout;

  @Bean
  public ApmAgent apmAgent () {
    final ApmAgent apmAgent = new ApmAgent ();
    apmAgent.setApmHost (apmHost);
    apmAgent.setAppName (appName);
    apmAgent.setSecretToken (secretToken);
    apmAgent.setInitialDelay (initialDelay);
    apmAgent.setPeriod (period);
    apmAgent.setQueueCapacity (queueCapacity);
    apmAgent.setPublishBatchSize (publishBatchSize);
    apmAgent.setEnqueueTimeout (enqueueTimeout);
    apmAgent.setFairQueue (fairQueue);

    return apmAgent;
  }
}
