package com.pharmaprice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.AuthProvider;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.report.repository.UploadedFileRepository;

/**
 * 엔티티-스키마 매핑을 실제 DB(pharmaprice_test)로 검증한다(ROADMAP T-06 완료 판정).
 */
class DomainMappingTest extends AbstractIntegrationTest {

	@Autowired
	EntityManager em;
	@Autowired
	RegionRepository regionRepository;
	@Autowired
	PharmacyRepository pharmacyRepository;
	@Autowired
	DrugRepository drugRepository;
	@Autowired
	AppUserRepository appUserRepository;
	@Autowired
	UploadedFileRepository uploadedFileRepository;
	@Autowired
	PriceReportRepository priceReportRepository;
	@Autowired
	PharmacyDrugPriceStatRepository statRepository;

	Map<String, List<String>> hours = Map.of("mon", List.of("09:00", "19:00"), "sat", List.of("10:00", "14:00"));
	AppUser user;
	PriceReport report;
	Pharmacy pharmacy;
	Drug drug;
	Long statId;

	@BeforeEach
	void setUp() {
		Region region = regionRepository.save(Region.builder()
				.code("11680").sido("서울특별시").sigungu("강남구").centerLat(37.5172).centerLng(127.0473).build());
		pharmacy = pharmacyRepository.save(Pharmacy.builder()
				.hiraCode("H001").name("강남약국").region(region).lat(37.5).lng(127.0).businessHours(hours).build());
		drug = drugRepository.save(Drug.builder()
				.name("타이레놀정500밀리그람").displayName("타이레놀 500mg").category("해열진통").packageUnit("10정").build());
		// V2__seed_master.sql이 user01~user20@example.com을 이미 심어두므로 겹치지 않는 이메일을 쓴다.
		user = appUserRepository.save(AppUser.builder()
				.email("domain-mapping-test@example.com").passwordHash("hash").nickname("제보자1").build());
		UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
				.originalName("r.jpg").storedPath("/uploads/r.jpg").contentType("image/jpeg").sizeBytes(1024).uploadedBy(user).build());
		report = priceReportRepository.save(PriceReport.builder()
				.pharmacy(pharmacy).drug(drug).user(user).price(3500).purchasedAt(LocalDate.now()).receiptFile(file).build());
		statId = statRepository.save(PharmacyDrugPriceStat.builder()
				.pharmacy(pharmacy).drug(drug).repPrice(3500).minPrice(3500).maxPrice(3500).avgPrice(3500)
				.reportCount(1).lastReportedAt(LocalDate.now()).windowDays((short) 90).calculatedAt(OffsetDateTime.now()).build())
				.getId();
		em.flush();
		em.clear();
	}

	@Test
	void 모든_엔티티가_저장_조회되고_JSONB가_Map으로_왕복한다() {
		Pharmacy foundPharmacy = pharmacyRepository.findById(pharmacy.getId()).orElseThrow();
		assertThat(foundPharmacy.getBusinessHours()).isEqualTo(hours);
		assertThat(foundPharmacy.getRegion().getSigungu()).isEqualTo("강남구");
		assertThat(foundPharmacy.getCreatedAt()).isNotNull();

		PriceReport foundReport = priceReportRepository.findById(report.getId()).orElseThrow();
		assertThat(foundReport.getDrug().getDisplayName()).isEqualTo("타이레놀 500mg");
		assertThat(foundReport.getUser().getProvider()).isEqualTo(AuthProvider.LOCAL);
		assertThat(foundReport.getReceiptFile().getSizeBytes()).isEqualTo(1024);
		assertThat(foundReport.getCreatedAt()).isNotNull();

		// V3__seed_prices.sql이 이 테이블을 이미 수천 건 채워두므로 전체 개수가 아니라
		// 이 테스트가 만든 행이 실제로 존재하는지만 확인한다.
		assertThat(statRepository.findById(statId)).isPresent();
	}

	@Test
	void enum은_DB에_문자열로_저장된다() {
		Object[] row = (Object[]) em.createNativeQuery("SELECT status, source FROM price_report WHERE id = ?")
				.setParameter(1, report.getId()).getSingleResult();
		assertThat(row).containsExactly("ACTIVE", "FORM");
	}

	@Test
	void delete하면_deleted_at이_채워지고_조회에서_빠진다() {
		priceReportRepository.delete(priceReportRepository.findById(report.getId()).orElseThrow());
		appUserRepository.delete(appUserRepository.findById(user.getId()).orElseThrow());
		em.flush();
		em.clear();

		assertThat(priceReportRepository.findById(report.getId())).isEmpty();
		assertThat(appUserRepository.findById(user.getId())).isEmpty();
		assertThat(em.createNativeQuery("SELECT count(*) FROM price_report WHERE deleted_at IS NOT NULL").getSingleResult())
				.isEqualTo(1L);
		assertThat(em.createNativeQuery("SELECT count(*) FROM app_user WHERE deleted_at IS NOT NULL").getSingleResult())
				.isEqualTo(1L);
	}
}
