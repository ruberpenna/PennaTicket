package com.example.ticketing.performance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
import com.example.ticketing.performance.PerformanceDtos.CreatePerformanceRequest;
import com.example.ticketing.performance.PerformanceDtos.PerformanceResponse;
import com.example.ticketing.performance.PerformanceDtos.SeatResponse;
import com.example.ticketing.performance.PerformanceDtos.SectionRequest;
import com.example.ticketing.performance.PerformanceDtos.UpdatePerformanceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PerformanceService {

	private final PerformanceRepository performanceRepository;
	private final SeatRepository seatRepository;

	// 생성자가 하나면 @Autowired 생략 가능
	public PerformanceService(PerformanceRepository performanceRepository, SeatRepository seatRepository) {
		this.performanceRepository = performanceRepository;
		this.seatRepository = seatRepository;
	}

	public List<PerformanceResponse> findAll() {
		return performanceRepository.findAll().stream().map(PerformanceResponse::from).toList();
	}

	public PerformanceResponse findById(Long performanceId) {
		return PerformanceResponse.from(getPerformance(performanceId));
	}

	public List<SeatResponse> findSeats(Long performanceId) {
		if (!performanceRepository.existsById(performanceId)) {
			throw new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND);
		}
		return seatRepository.findByPerformanceIdOrderBySectionAscIdAsc(performanceId)
			.stream()
			.map(SeatResponse::from)
			.toList();
	}

	@Transactional
	public PerformanceResponse create(CreatePerformanceRequest request) {
		validateUniqueSections(request.sections());

		Performance performance = performanceRepository
			.save(new Performance(request.title(), request.venue(), request.startAt()));

		List<Seat> seats = request.sections()
			.stream()
			.flatMap(section -> IntStream.rangeClosed(1, section.seatCount())
				.mapToObj(number -> new Seat(performance, section.section(), String.valueOf(number),
						section.grade(), section.price())))
			.toList();
		seatRepository.saveAll(seats);

		return PerformanceResponse.from(performance);
	}

	@Transactional
	public PerformanceResponse update(Long performanceId, UpdatePerformanceRequest request) {
		Performance performance = getPerformance(performanceId);
		// 더티 체킹: 트랜잭션이 끝날 때 바뀐 필드가 UPDATE 된다 (save 호출 불필요)
		performance.changeInfo(request.title(), request.venue(), request.startAt());
		return PerformanceResponse.from(performance);
	}

	private Performance getPerformance(Long performanceId) {
		return performanceRepository.findById(performanceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
	}

	private static void validateUniqueSections(List<SectionRequest> sections) {
		Set<String> names = new HashSet<>();
		for (SectionRequest section : sections) {
			if (!names.add(section.section())) {
				throw new BusinessException(ErrorCode.DUPLICATE_SECTION);
			}
		}
	}

}
