package com.pixora.backend;

import com.pixora.backend.entity.User;
import com.pixora.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PixoraBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setup() {
		userRepository.deleteAll();
	}

	@Test
	void healthEndpointIsPublicAndReturnsOk() throws Exception {
		mockMvc.perform(get("/api/health")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void protectedEndpointReturns401WhenUnauthenticated() throws Exception {
		mockMvc.perform(get("/api/photos/status")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void authGoogleEndpointRegistersAndReturnsUser() throws Exception {
		String testToken = "test-token-alice";

		mockMvc.perform(post("/api/auth/google")
						.header("Authorization", "Bearer " + testToken)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firebaseUid").value("test-uid-alice"))
				.andExpect(jsonPath("$.email").value("alice@pixora.app"));

		assertTrue(userRepository.findByFirebaseUid("test-uid-alice").isPresent());
	}

	@Test
	void protectedEndpointSucceedsWithValidToken() throws Exception {
		String testToken = "test-token-bob";

		mockMvc.perform(get("/api/photos/status")
						.header("Authorization", "Bearer " + testToken)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.userEmail").value("bob@pixora.app"));
	}

}
