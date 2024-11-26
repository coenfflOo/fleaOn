package com.ssafy.fleaOn;

import com.ssafy.fleaOn.web.controller.PurchaseApiController;
import com.ssafy.fleaOn.web.domain.Live;
import com.ssafy.fleaOn.web.domain.Product;
import com.ssafy.fleaOn.web.domain.RegionInfo;
import com.ssafy.fleaOn.web.domain.User;
import com.ssafy.fleaOn.web.dto.PurchaseRequest;
import com.ssafy.fleaOn.web.producer.RedisQueueProducer;
import com.ssafy.fleaOn.web.repository.LiveRepository;
import com.ssafy.fleaOn.web.repository.ProductRepository;
import com.ssafy.fleaOn.web.repository.RegionInfoRepository;
import com.ssafy.fleaOn.web.repository.UserRepository;
import com.ssafy.fleaOn.web.service.PurchaseService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@Transactional
public class LoadTest {

    @Autowired
    private RedisQueueProducer redisQueueProducer;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionInfoRepository regionInfoRepository;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private LiveRepository liveRepository;

    private int testProductId;
    @Autowired
    private PurchaseApiController purchaseApiController;

    @BeforeEach
    public void setup() {
        String uniqueEmail = "seller" + System.currentTimeMillis() + "@test.com";
        User seller = userRepository.findByEmail("seller@test.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(uniqueEmail)
                        .userIdentifier("USER")
                        .role("USER")
                        .name("Test Seller")
                        .build()));

        RegionInfo regionInfo = regionInfoRepository.findByRegionCode("1111010200").get();

        Live live = liveRepository.save(Live.builder()
                .title("Test Live")
                .liveDate(LocalDateTime.now()) // liveDate 값 설정
                .tradePlace("Online")
                .seller(seller)
                .regionInfo(regionInfo) // 지역 정보 필요 시 설정
                .isLive(0)
                .viewCount(0)
                .build());

        Product product = productRepository.save(Product.builder()
                .live(live)
                .user(seller)
                .name("Test Product")
                .price(1000)
                .firstCategoryId(1)
                .secondCategoryId(2)
                .build());

        testProductId = 452;
        Assertions.assertNotNull(testProductId, "Test Product ID should not be null");
        Assertions.assertTrue(productRepository.existsById(testProductId), "Test Product should exist in the database");
    }

    @BeforeEach
    public void clearRedisQueue() {
        redisTemplate.delete("purchaseQueue"); // Redis에 저장된 Queue 초기화
    }

    @BeforeAll
    public static void setupBulkData(@Autowired ProductRepository productRepository,
                                     @Autowired UserRepository userRepository,
                                     @Autowired LiveRepository liveRepository,
                                     @Autowired RegionInfoRepository regionInfoRepository) {
        User seller = userRepository.findByEmail("bulk@test.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("bulk@test.com")
                        .userIdentifier("USER")
                        .role("USER")
                        .name("Bulk Seller")
                        .build()));

        RegionInfo regionInfo = regionInfoRepository.findByRegionCode("1111010200")
                .orElseThrow(() -> new IllegalArgumentException("Region code not found: 1111010200"));

        Live live = liveRepository.save(Live.builder()
                .title("Bulk Live")
                .liveDate(LocalDateTime.now())
                .tradePlace("Online")
                .seller(seller)
                .regionInfo(regionInfo)
                .isLive(0)
                .viewCount(0)
                .build());

        for (int i = 100; i < 50100; i++) {
            String email = "user" + i + "@test.com";
            int finalI = i;
            userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .email(email)
                            .userIdentifier("USER")
                            .role("USER")
                            .name("Test User " + finalI)
                            .build()));
        }

        for (int i = 1; i <= 50000; i++) {
            productRepository.save(Product.builder()
                    .live(live)
                    .user(seller)
                    .name("Bulk Product " + i)
                    .price(1000)
                    .firstCategoryId(1)
                    .secondCategoryId(2)
                    .build());
        }
    }

    @Test
    public void loadTestPurchaseRequests() throws InterruptedException {
        int threadCount = 50000; // 동시 요청 수
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        long startTime = System.currentTimeMillis(); // 시작 시간 기록

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int userId = 40217 + i;
            futures.add(executorService.submit(() -> {
                try {
                    PurchaseRequest request = new PurchaseRequest(356, userId);
                    redisQueueProducer.sendPurchaseRequest(request); // Redis 큐에 구매 요청 추가
                    successfulRequests.incrementAndGet();
                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(); // 모든 요청이 완료될 때까지 대기
            } catch (Exception ignored) {
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS); // 30초 내로 모든 작업 종료 대기

        long endTime = System.currentTimeMillis(); // 종료 시간 기록
        System.out.println("All purchase requests sent to Redis.");
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Failed requests: " + failedRequests.get());
        System.out.println("Test duration: " + (endTime - startTime) + " ms");
        System.out.println("------------------------------------------------------");
    }

    @Test
    public void loadTestDatabasePurchaseRequests() throws InterruptedException {
        int threadCount = 50000; // 동시 요청 수
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int userId = 40217 + i;
            futures.add(executorService.submit(() -> {
                try {
                    PurchaseRequest request = new PurchaseRequest(365, userId);
                    purchaseService.processPurchaseRequest(request); // DB 기반 구매 로직 호출
                    successfulRequests.incrementAndGet();
                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        System.out.println("All purchase requests sent to Database.");
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Failed requests: " + failedRequests.get());
        System.out.println("Test duration: " + (endTime - startTime) + " ms");
        System.out.println("------------------------------------------------------");

        assertEquals(threadCount, successfulRequests.get() + failedRequests.get(), "All requests should be processed.");
    }

    @Test
    public void loadTestWithRedisPipelining() throws InterruptedException {
        int requestCount = 50000; // 총 요청 수
        List<PurchaseRequest> requests = new ArrayList<>();
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        // 요청 리스트 생성
        for (int i = 0; i < requestCount; i++) {
            requests.add(new PurchaseRequest(478, 40217 + i));
        }

        long startTime = System.currentTimeMillis();

        // Redis Pipelining을 사용하는 batchBuy 호출
        try {
            purchaseApiController.batchBuy(requests).forEach(result -> {
                if (result) { // 성공 시
                    successfulRequests.incrementAndGet();
                } else { // 실패 시
                    failedRequests.incrementAndGet();
                }
            });
        } catch (Exception e) {
            // 모든 요청이 실패한 경우
            failedRequests.addAndGet(requestCount);
            System.err.println("Batch buy failed: " + e.getMessage());
        }

        long endTime = System.currentTimeMillis();

        System.out.println("All purchase requests sent to Redis using Pipelining.");
        System.out.println("Total requests: " + requestCount);
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Failed requests: " + failedRequests.get());
        System.out.println("Test duration: " + (endTime - startTime) + " ms");
        System.out.println("------------------------------------------------------");

        // 결과 검증
        assertEquals(requestCount, successfulRequests.get() + failedRequests.get(), "All requests should be processed.");
    }

}

