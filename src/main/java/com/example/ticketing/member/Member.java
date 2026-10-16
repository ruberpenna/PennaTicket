package com.example.ticketing.member;

import com.example.ticketing.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	/** 평문이 아니라 PasswordEncoder로 해시한 값 */
	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MemberRole role;

	protected Member() {
	}

	public Member(String email, String encodedPassword, String name) {
		this.email = email;
		this.password = encodedPassword;
		this.name = name;
		this.role = MemberRole.USER;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPassword() {
		return password;
	}

	public String getName() {
		return name;
	}

	public MemberRole getRole() {
		return role;
	}

}
