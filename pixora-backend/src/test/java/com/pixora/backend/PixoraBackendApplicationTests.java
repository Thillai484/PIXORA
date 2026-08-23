package com.pixora.backend;

import com.pixora.backend.entity.Photo;
import com.pixora.backend.entity.PhotoRequest;
import com.pixora.backend.entity.User;
import com.pixora.backend.repository.PhotoRepository;
import com.pixora.backend.repository.PhotoRequestRepository;
import com.pixora.backend.repository.UserRepository;
import com.pixora.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PixoraBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private PhotoRequestRepository photoRequestRepository;

	@Autowired
	private StorageService storageService;

	@BeforeEach
	void setup() {
		photoRequestRepository.deleteAll();
		photoRepository.deleteAll();
		userRepository.deleteAll();
	}

	private byte[] createTestImageBytes(String format) throws Exception {
		BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, 120, 120);
		g.dispose();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(image, format, baos);
		return baos.toByteArray();
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

	@Test
	void photoAndPhotoRequestRepositoriesWorkCorrectly() {
		User user = userRepository.save(User.builder()
				.firebaseUid("uid-test-123")
				.email("photo.user@pixora.app")
				.name("Photo User")
				.build());

		Photo photo = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("https://supabase.co/storage/v1/object/public/photos/sample.jpg")
				.mode("PROFESSIONAL")
				.photoType("RESUME")
				.style("CORPORATE")
				.clothing("BLAZER")
				.background("OFFICE")
				.status("UPLOADED")
				.build());

		assertNotNull(photo.getId());
		assertNotNull(photo.getCreatedAt());

		PhotoRequest request = photoRequestRepository.save(PhotoRequest.builder()
				.userId(user.getId())
				.photoId(photo.getId())
				.requestType("SINGLE_PHOTO")
				.status("PROCESSING")
				.build());

		assertNotNull(request.getId());

		List<Photo> photos = photoRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
		assertEquals(1, photos.size());
		assertEquals("RESUME", photos.get(0).getPhotoType());

		List<PhotoRequest> requests = photoRequestRepository.findByPhotoId(photo.getId());
		assertEquals(1, requests.size());
		assertEquals("PROCESSING", requests.get(0).getStatus());
	}

	@Test
	void photoUploadFlowSucceedsWithValidImage() throws Exception {
		byte[] validJpeg = createTestImageBytes("jpg");
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"portrait.jpg",
				"image/jpeg",
				validJpeg
		);

		mockMvc.perform(multipart("/api/photos/upload")
						.file(file)
						.header("Authorization", "Bearer test-token-carol"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.photoId").isNumber())
				.andExpect(jsonPath("$.status").value("UPLOADED"))
				.andExpect(jsonPath("$.originalImageUrl").isNotEmpty());

		List<Photo> photos = photoRepository.findAll();
		assertEquals(1, photos.size());
		assertEquals("UPLOADED", photos.get(0).getStatus());
	}

	@Test
	void photoUploadRejectsInvalidNonImage() throws Exception {
		MockMultipartFile fakeFile = new MockMultipartFile(
				"file",
				"bad-file.txt",
				"text/plain",
				"This is definitely not an image".getBytes()
		);

		mockMvc.perform(multipart("/api/photos/upload")
						.file(fakeFile)
						.header("Authorization", "Bearer test-token-dave"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FORMAT"));
	}

	@Test
	void photoUploadRejectsUnauthenticated() throws Exception {
		byte[] validJpeg = createTestImageBytes("jpg");
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"portrait.jpg",
				"image/jpeg",
				validJpeg
		);

		mockMvc.perform(multipart("/api/photos/upload")
						.file(file))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void testStorageServiceUploadAndTestController() throws Exception {
		MockMultipartFile sampleFile = new MockMultipartFile(
				"file",
				"test-portrait.jpg",
				"image/jpeg",
				"fake-jpeg-binary-data".getBytes()
		);

		mockMvc.perform(multipart("/api/test/upload")
						.file(sampleFile))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.filename").value("test-portrait.jpg"))
				.andExpect(jsonPath("$.publicUrl").isNotEmpty());
	}
}
