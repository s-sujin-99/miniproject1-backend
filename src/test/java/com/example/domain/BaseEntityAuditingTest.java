package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.AbstractIntegrationTest;

/**
 * BaseTimeEntity 감사 컬럼 자동 채움과 BaseEntity Soft Delete 헬퍼를 실제 DB(pharmaprice_test)로 검증한다.
 */
class BaseEntityAuditingTest extends AbstractIntegrationTest {

	@Autowired
	EntityManager em;

	@Test
	void 저장하면_createdAt_updatedAt이_채워지고_softDelete하면_deletedAt이_채워진다() {
		TestOnlyEntity entity = new TestOnlyEntity();
		em.persist(entity);
		em.flush();
		em.clear();

		TestOnlyEntity found = em.find(TestOnlyEntity.class, entity.id);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
		assertThat(found.isDeleted()).isFalse();

		found.softDelete();
		em.flush();
		em.clear();

		TestOnlyEntity deleted = em.find(TestOnlyEntity.class, entity.id);
		assertThat(deleted.getDeletedAt()).isNotNull();
		assertThat(deleted.isDeleted()).isTrue();
	}

	/** 검증 전용 엔티티. 도메인 엔티티로 오인되지 않도록 테스트 클래스 안에만 둔다. */
	@Entity
	@Table(name = "test_only_entity")
	static class TestOnlyEntity extends BaseEntity {

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		Long id;
	}
}
