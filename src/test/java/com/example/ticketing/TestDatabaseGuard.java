package com.example.ticketing;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 안전장치: 테스트가 운영 DB에 데이터를 쓰지 않도록, DB 이름에 "test"가 없으면 테스트를 시작하지 않는다.
 * (./config/application.properties가 테스트 설정보다 우선하므로, config/application-test.properties를 빠뜨리면
 * 운영 DB 주소가 그대로 쓰일 수 있다)
 *
 * src/test 아래의 @Configuration이라 테스트에서만 컴포넌트 스캔으로 등록된다.
 */
@Configuration(proxyBeanMethods = false)
class TestDatabaseGuard implements InitializingBean {

	private final DataSourceProperties dataSourceProperties;

	TestDatabaseGuard(DataSourceProperties dataSourceProperties) {
		this.dataSourceProperties = dataSourceProperties;
	}

	@Override
	public void afterPropertiesSet() {
		String url = dataSourceProperties.getUrl();
		String database = url.substring(url.lastIndexOf('/') + 1);
		if (!database.contains("test")) {
			throw new IllegalStateException("테스트가 운영 DB(" + database + ")를 가리키고 있습니다. "
					+ "config/application-test.properties에 테스트 DB 주소를 지정하세요. (config-example 참고)");
		}
	}

}
