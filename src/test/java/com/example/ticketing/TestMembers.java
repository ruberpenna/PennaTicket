package com.example.ticketing;

import java.util.UUID;

import com.example.ticketing.member.Member;
import com.example.ticketing.member.MemberRepository;

/** 예매에는 실제 회원이 필요하다(reservation.member_id FK). 테스트용 회원을 만든다. */
public final class TestMembers {

	private TestMembers() {
	}

	/** 로그인하지 않는 테스트용이라 비밀번호는 의미 없는 값 ({noop} = 해시 안 함) */
	public static Long create(MemberRepository memberRepository) {
		String email = "member-" + UUID.randomUUID() + "@test.local";
		return memberRepository.save(new Member(email, "{noop}unused", "테스트회원")).getId();
	}

}
