package com.prathamesh.jbsolar;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.prathamesh.jbsolar.api.dto.AgentRequest;
import com.prathamesh.jbsolar.api.dto.FarmerRequest;
import com.prathamesh.jbsolar.api.dto.VendorRequest;
import com.prathamesh.jbsolar.service.AgentService;
import com.prathamesh.jbsolar.service.FarmerService;
import com.prathamesh.jbsolar.service.VendorService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = "app.cors.allowed-origins=https://jbsolar-pm.vercel.app,http://localhost:8081")
@ActiveProfiles("test")
class JbsolarApplicationTests {

	@LocalServerPort
	private int port;
	@Autowired
	private VendorService vendors;
	@Autowired
	private AgentService agents;
	@Autowired
	private FarmerService farmers;

	@Test
	void contextLoads() {
	}

	@Test
	void allowsCorsPreflightForLoginFromConfiguredFrontend() throws Exception {
		assertPreflightAllowed("https://jbsolar-pm.vercel.app");
		assertPreflightAllowed("http://localhost:8081");
	}

	private void assertPreflightAllowed(String origin) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/v1/auth/agent/login"))
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
				.header("Origin", origin)
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type,authorization")
				.build();

		HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
		assertEquals(200, response.statusCode());
		assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
	}

	@Test
	void agentCanReadFarmerWithBearerToken() throws Exception {
		String suffix = UUID.randomUUID().toString();
		var vendor = vendors.create(new VendorRequest("CORS auth test " + suffix, null,
				null, suffix + "@example.test"));
		String mobile = "9" + String.format("%09d",
				Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L));
		var agent = agents.create(new AgentRequest(vendor.id(), "CORS auth agent", mobile,
				"long-enough-test-password"));
		var farmer = farmers.create(new FarmerRequest("CORS auth farmer", "9876543210",
				"456789123456", null, null, null, null),
				new com.prathamesh.jbsolar.security.UserPrincipal(agent.userId(), agent.id(), vendor.id(),
						agent.mobile(), com.prathamesh.jbsolar.domain.UserRole.VENDOR_AGENT));

		HttpResponse<String> loginResponse = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/v1/auth/agent/login"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						"{\"mobile\":\"" + mobile + "\",\"password\":\"long-enough-test-password\"}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, loginResponse.statusCode());
		String token = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
				.readTree(loginResponse.body()).get("accessToken").asText();

		HttpResponse<String> farmerResponse = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/v1/farmers/" + farmer.id()))
				.header("Authorization", "Bearer " + token)
				.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, farmerResponse.statusCode(), farmerResponse.body());

		HttpResponse<String> companyResponse = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/v1/vendors/" + vendor.id()))
				.header("Authorization", "Bearer " + token)
				.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, companyResponse.statusCode(), companyResponse.body());

		var otherVendor = vendors.create(new VendorRequest("Other vendor " + suffix, null, null,
				"other-" + suffix + "@example.test"));
		HttpResponse<String> otherCompanyResponse = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/v1/vendors/" + otherVendor.id()))
				.header("Authorization", "Bearer " + token)
				.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(404, otherCompanyResponse.statusCode(), otherCompanyResponse.body());

		agents.create(new AgentRequest(vendor.id(), "Another company agent", "9777777777",
				"long-enough-test-password"));
		String encodedMobile = URLEncoder.encode(agent.mobile(), StandardCharsets.UTF_8);
		HttpResponse<String> agentSearchResponse = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port
						+ "/api/v1/agents?page=0&size=100&search=" + encodedMobile))
				.header("Authorization", "Bearer " + token)
				.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, agentSearchResponse.statusCode(), agentSearchResponse.body());
		var agentSearchBody = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
				.readTree(agentSearchResponse.body());
		assertEquals(1, agentSearchBody.get("totalElements").asInt());
		assertEquals(agent.id().toString(), agentSearchBody.get("content").get(0).get("id").asText());
		assertEquals(agent.mobile(), agentSearchBody.get("content").get(0).get("mobile").asText());
	}
}
