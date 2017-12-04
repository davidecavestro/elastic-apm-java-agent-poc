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
  @Value ("{elastic.apm.initialdelay:5000}")
  private long initialDelay;
  @Value ("${elastic.apm.period:1000}")
  private long period;
  @Value ("${elastic.apm.queuecapacity:10000}")
  private int queueCapacity;

  @Bean
  public ApmAgent apmAgent () {
    final ApmAgent apmAgent = new ApmAgent ();
    apmAgent.setApmHost (apmHost);
    apmAgent.setAppName (appName);
    apmAgent.setSecretToken (secretToken);
    apmAgent.setInitialDelay (initialDelay);
    apmAgent.setPeriod (period);
    apmAgent.setQueueCapacity (queueCapacity);

    return apmAgent;
  }
}
