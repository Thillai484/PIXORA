package com.pixora.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixora.backend.dto.CustomizePhotoRequest;
import com.pixora.backend.dto.PhotoPackRequest;
import com.pixora.backend.entity.Photo;
import com.pixora.backend.entity.PhotoRequest;
import com.pixora.backend.entity.User;
import com.pixora.backend.repository.PhotoRepository;
import com.pixora.backend.repository.PhotoRequestRepository;
import com.pixora.backend.repository.UserRepository;
import com.pixora.backend.service.AIService;
import com.pixora.backend.service.StorageService;
import com.pixora.backend.util.PromptBuilderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

	@Autowired
	private AIService aiService;

	@Autowired
	private ObjectMapper objectMapper;

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
	void photoCustomizationOfficialModeSetsNormalizedPresets() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-elena")
				.email("elena@pixora.app")
				.name("Elena")
				.build());

		Photo photo = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/sample.jpg")
				.status("UPLOADED")
				.build());

		CustomizePhotoRequest request = CustomizePhotoRequest.builder()
				.photoId(photo.getId())
				.mode("OFFICIAL")
				.photoType("PASSPORT")
				.build();

		mockMvc.perform(post("/api/photos/customize")
						.header("Authorization", "Bearer test-token-elena")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(photo.getId()))
				.andExpect(jsonPath("$.mode").value("OFFICIAL"))
				.andExpect(jsonPath("$.photoType").value("PASSPORT"))
				.andExpect(jsonPath("$.style").value("STUDIO"))
				.andExpect(jsonPath("$.clothing").value("FORMAL_SHIRT"))
				.andExpect(jsonPath("$.background").value("WHITE"))
				.andExpect(jsonPath("$.status").value("CONFIGURED"));
	}

	@Test
	void photoCustomizationProfessionalModeSetsCustomOptions() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-frank")
				.email("frank@pixora.app")
				.name("Frank")
				.build());

		Photo photo = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/sample2.jpg")
				.status("UPLOADED")
				.build());

		CustomizePhotoRequest request = CustomizePhotoRequest.builder()
				.photoId(photo.getId())
				.mode("PROFESSIONAL")
				.style("CREATIVE")
				.clothing("SUIT")
				.background("OUTDOOR_BLUR")
				.build();

		mockMvc.perform(post("/api/photos/customize")
						.header("Authorization", "Bearer test-token-frank")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(photo.getId()))
				.andExpect(jsonPath("$.mode").value("PROFESSIONAL"))
				.andExpect(jsonPath("$.style").value("CREATIVE"))
				.andExpect(jsonPath("$.clothing").value("SUIT"))
				.andExpect(jsonPath("$.background").value("OUTDOOR_BLUR"))
				.andExpect(jsonPath("$.status").value("CONFIGURED"));
	}

	@Test
	void promptBuilderConstructsValidPrompts() {
		Photo passportPhoto = Photo.builder()
				.mode("OFFICIAL")
				.photoType("PASSPORT")
				.build();
		String passportPrompt = PromptBuilderUtil.buildPrompt(passportPhoto);
		assertTrue(passportPrompt.contains("biometric passport"));
		assertTrue(passportPrompt.contains("white background"));

		Photo customPhoto = Photo.builder()
				.mode("PROFESSIONAL")
				.style("STUDIO")
				.clothing("SUIT")
				.background("OFFICE")
				.build();
		String customPrompt = PromptBuilderUtil.buildPrompt(customPhoto);
		assertTrue(customPrompt.contains("suit"));
		assertTrue(customPrompt.contains("office"));
	}

	@Test
	void photoGenerationStartsAndStatusIsRetrievable() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-george")
				.email("george@pixora.app")
				.name("George")
				.build());

		Photo photo = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/george.jpg")
				.mode("OFFICIAL")
				.photoType("RESUME")
				.style("CORPORATE")
				.clothing("BLAZER")
				.background("STUDIO")
				.status("CONFIGURED")
				.build());

		// Start generation -> 202 Accepted
		mockMvc.perform(post("/api/photos/" + photo.getId() + "/generate")
						.header("Authorization", "Bearer test-token-george"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.photoId").value(photo.getId()))
				.andExpect(jsonPath("$.status").value("PROCESSING"));

		// Check status endpoint
		mockMvc.perform(get("/api/photos/" + photo.getId() + "/status")
						.header("Authorization", "Bearer test-token-george"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.photoId").value(photo.getId()));
	}

	@Test
	void downloadPhotoEndpointReturnsAttachmentHeaders() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-harry")
				.email("harry@pixora.app")
				.name("Harry")
				.build());

		Photo photo = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/harry.jpg")
				.generatedImageUrl("http://localhost:8080/storage/photos/photos/" + user.getId() + "/generated/sample.png")
				.status("DONE")
				.build());

		mockMvc.perform(get("/api/photos/" + photo.getId() + "/download")
						.header("Authorization", "Bearer test-token-harry"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pixora-portrait-" + photo.getId() + ".png\""))
				.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	@Test
	void getUserPhotosRetrievesUserGalleryList() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-ian")
				.email("ian@pixora.app")
				.name("Ian")
				.build());

		photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/photo1.jpg")
				.mode("OFFICIAL")
				.photoType("RESUME")
				.status("DONE")
				.build());

		photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/photo2.jpg")
				.mode("OFFICIAL")
				.photoType("PASSPORT")
				.status("DONE")
				.build());

		mockMvc.perform(get("/api/photos")
						.header("Authorization", "Bearer test-token-ian"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void deletePhotoDeletesRecordAndAssociatedRequests() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-julia")
				.email("julia@pixora.app")
				.name("Julia")
				.build());

		Photo photo = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/julia.jpg")
				.status("DONE")
				.build());

		photoRequestRepository.save(PhotoRequest.builder()
				.userId(user.getId())
				.photoId(photo.getId())
				.requestType("SINGLE_PHOTO")
				.status("COMPLETED")
				.build());

		mockMvc.perform(delete("/api/photos/" + photo.getId())
						.header("Authorization", "Bearer test-token-julia"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		assertFalse(photoRepository.findById(photo.getId()).isPresent());
		assertTrue(photoRequestRepository.findByPhotoId(photo.getId()).isEmpty());
	}

	@Test
	void photoPackGenerationDispatchesMultiplePhotos() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-kyle")
				.email("kyle@pixora.app")
				.name("Kyle")
				.build());

		Photo photo = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/kyle.jpg")
				.status("UPLOADED")
				.build());

		PhotoPackRequest packReq = PhotoPackRequest.builder()
				.packType("PROFESSIONAL_PACK")
				.build();

		mockMvc.perform(post("/api/photos/" + photo.getId() + "/pack")
						.header("Authorization", "Bearer test-token-kyle")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(packReq)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.totalPhotos").value(3))
				.andExpect(jsonPath("$.generatedPhotoIds.length()").value(3));
	}

	@Test
	void downloadPackZipReturnsValidZipMediaType() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-laura")
				.email("laura@pixora.app")
				.name("Laura")
				.build());

		Photo p1 = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl("http://localhost:8080/storage/original/p1.jpg")
				.photoType("RESUME")
				.status("DONE")
				.build());

		mockMvc.perform(get("/api/photos/pack/zip?ids=" + p1.getId())
						.header("Authorization", "Bearer test-token-laura"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pixora-photo-pack.zip\""))
				.andExpect(content().contentType("application/zip"));
	}

	@Test
	void generationWithDifferentPresetsProducesDistinctRequestsAndUrls() throws Exception {
		User user = userRepository.save(User.builder()
				.firebaseUid("test-uid-marcus")
				.email("marcus@pixora.app")
				.name("Marcus")
				.build());

		byte[] validJpeg = createTestImageBytes("jpg");
		String originalUrl = storageService.uploadOriginalPhoto(user.getId(), "marcus.jpg", validJpeg, "image/jpeg");

		Photo photo = photoRepository.save(Photo.builder()
				.userId(user.getId())
				.originalImageUrl(originalUrl)
				.status("UPLOADED")
				.build());

		// 1. Customize & Generate Passport (Official Mode)
		CustomizePhotoRequest passportReq = CustomizePhotoRequest.builder()
				.photoId(photo.getId())
				.mode("OFFICIAL")
				.photoType("PASSPORT")
				.build();

		mockMvc.perform(post("/api/photos/customize")
						.header("Authorization", "Bearer test-token-marcus")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(passportReq)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/photos/" + photo.getId() + "/generate")
						.header("Authorization", "Bearer test-token-marcus"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.success").value(true));

		// 2. Customize & Generate Resume (Professional Mode)
		CustomizePhotoRequest resumeReq = CustomizePhotoRequest.builder()
				.photoId(photo.getId())
				.mode("OFFICIAL") // preset selector
				.photoType("RESUME")
				.build();

		mockMvc.perform(post("/api/photos/customize")
						.header("Authorization", "Bearer test-token-marcus")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(resumeReq)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/photos/" + photo.getId() + "/generate")
						.header("Authorization", "Bearer test-token-marcus"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.success").value(true));

		// Verify 2 distinct PhotoRequests were created for the same photo
		List<PhotoRequest> requests = photoRequestRepository.findByPhotoId(photo.getId());
		assertEquals(2, requests.size());
	}
}
