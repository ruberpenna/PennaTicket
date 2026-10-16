package com.example.ticketing.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** MockMvcTester: 예전 perform().andExpect() 체인 대신 AssertJ 스타일로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPerformanceControllerTest {

	// text block: 예전처럼 "\"title\":" 이스케이프 지옥 없이 JSON을 그대로 쓴다
	private static final String CREATE_BODY = """
			{
			  "title": "오페라의 유령",
			  "venue": "샤롯데씨어터",
			  "startAt": "2099-12-24T19:00:00",
			  "sections": [
			    { "section": "A", "grade": "VIP", "price": 190000, "seatCount": 5 },
			    { "section": "B", "grade": "R",   "price": 160000, "seatCount": 3 }
			  ]
			}
			""";

	@Autowired
	MockMvcTester mvc;

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자는_공연을_등록하면_구역별_좌석까지_생성된다() throws Exception {
		MvcTestResult created = post(CREATE_BODY);

		assertThat(created).hasStatus(HttpStatus.CREATED);
		assertThat(created).bodyJson().extractingPath("$.title").isEqualTo("오페라의 유령");
		Integer id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/performances/" + id);

		assertThat(mvc.get().uri("/api/performances/{id}/seats", id))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(8);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자는_공연_정보를_수정할_수_있다() throws Exception {
		Integer id = JsonPath.read(post(CREATE_BODY).getResponse().getContentAsString(), "$.id");

		assertThat(mvc.put()
			.uri("/api/admin/performances/{id}", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "title": "오페라의 유령 (앙코르)", "venue": "샤롯데씨어터", "startAt": "2099-12-31T19:00:00" }
					"""))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("오페라의 유령 (앙코르)");
	}

	@Test
	@WithMockUser(roles = "USER")
	void 일반_사용자는_403() {
		assertThat(post(CREATE_BODY)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void 로그인하지_않으면_401() {
		assertThat(post(CREATE_BODY)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 구역_이름이_중복되면_400() {
		MvcTestResult result = post(CREATE_BODY.replace("\"section\": \"B\"", "\"section\": \"A\""));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("DUPLICATE_SECTION");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 지난_날짜로는_등록할_수_없다() {
		assertThat(post(CREATE_BODY.replace("2099-12-24", "2000-01-01"))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	private MvcTestResult post(String body) {
		return mvc.post()
			.uri("/api/admin/performances")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

}
