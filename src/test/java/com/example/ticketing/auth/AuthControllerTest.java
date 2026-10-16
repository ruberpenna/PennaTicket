package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 가입 → 로그인 → 발급받은 진짜 JWT로 보호된 API 호출까지 전체 흐름 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

	private static final String PASSWORD = "test-password-1234";

	@Autowired
	MockMvcTester mvc;

	private String email;

	@BeforeEach
	void setUp() {
		email = "user" + System.nanoTime() + "@test.local"; // 실제 DB라 매번 다른 이메일
	}

	@Test
	void 가입하고_로그인해서_받은_토큰으로_내_정보를_조회한다() throws Exception {
		assertThat(signup(email)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.role")
			.isEqualTo("USER");

		MvcTestResult login = login(email, PASSWORD);
		assertThat(login).hasStatusOk().bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
		String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

		assertThat(mvc.get().uri("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo(email);
	}

	@Test
	void 비밀번호는_해시로_저장되고_응답에도_나오지_않는다() {
		assertThat(signup(email)).bodyJson().doesNotHavePath("$.password");
	}

	@Test
	void 같은_이메일로_다시_가입하면_409() {
		signup(email);

		assertThat(signup(email)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("DUPLICATE_EMAIL");
	}

	@Test
	void 비밀번호가_틀리면_401() {
		signup(email);

		assertThat(login(email, "wrong-password")).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void 비밀번호가_8자보다_짧으면_400() {
		assertThat(mvc.post()
			.uri("/api/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "email": "%s", "password": "short", "name": "테스터" }
					""".formatted(email))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void 토큰이_없거나_위조되면_401() {
		assertThat(mvc.get().uri("/api/members/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult signup(String email) {
		return mvc.post()
			.uri("/api/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "email": "%s", "password": "%s", "name": "테스터" }
					""".formatted(email, PASSWORD))
			.exchange();
	}

	private MvcTestResult login(String email, String password) {
		return mvc.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "email": "%s", "password": "%s" }
					""".formatted(email, password))
			.exchange();
	}

}
