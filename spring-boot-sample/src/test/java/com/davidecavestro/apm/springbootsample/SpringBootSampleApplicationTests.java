package com.davidecavestro.apm.springbootsample;

import com.davidecavestro.elastic.apm.spring.webmvc.ApmSpringService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.FileCopyUtils;
import wiremock.org.apache.http.HttpResponse;
import wiremock.org.apache.http.HttpStatus;
import wiremock.org.apache.http.ProtocolVersion;
import wiremock.org.apache.http.StatusLine;
import wiremock.org.apache.http.client.methods.HttpGet;
import wiremock.org.apache.http.client.methods.HttpUriRequest;
import wiremock.org.apache.http.impl.client.HttpClientBuilder;
import wiremock.org.apache.http.message.BasicHttpResponse;
import wiremock.org.apache.http.message.BasicStatusLine;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;

import static wiremock.org.hamcrest.CoreMatchers.equalTo;
import static wiremock.org.hamcrest.MatcherAssert.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Some integration tests for sample application
 */
@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest
public class SpringBootSampleApplicationTests {

	@Rule
	private WireMockRule wireMockRule = new WireMockRule (8200); // No-args constructor defaults to port 8080

	@Autowired
	private ApmSpringService apmSpringService;

	@Test
	public void contextLoads() {
	}

	@Test
	public void exampleTest() throws IOException {
		stubFor(get(urlEqualTo("/v1/transactions"))
//				.withHeader("Accept", WireMock.equalTo("application/json"))
				.willReturn(aResponse()
						.withStatus(202)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBody("")));

		// Given
		final HttpUriRequest request = new HttpGet ( "http://localhost:8080/");

		// When
		final BasicHttpResponse response = new BasicHttpResponse (new BasicStatusLine (new ProtocolVersion ("HTTP", 1, 1), 202, ""));
//		final HttpResponse httpResponse = apmSpringService.prepareTransaction (request, response, "202", 1000l);
//
//		// Then
//		assertThat(
//				httpResponse.getStatusLine().getStatusCode(),
//				equalTo(HttpStatus.SC_ACCEPTED));

		verify(postRequestedFor(urlMatching("/v1/transaction"))
				.withRequestBody(matchingJsonPath("$.app.agent[?(@.name == 'java')]"))
				.withHeader("Content-Type", notMatching("application/json")));
	}
}
