package com.ssafy.fleaOn.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fleaOn.web.domain.Shorts;
import com.ssafy.fleaOn.web.domain.User;
import com.ssafy.fleaOn.web.dto.*;
import com.ssafy.fleaOn.web.producer.RedisQueueProducer;
import com.ssafy.fleaOn.web.service.PurchaseService;
import com.ssafy.fleaOn.web.service.RedisService;
import com.ssafy.fleaOn.web.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/fleaon/purchase")
@Tag(name = "Purchase API", description = "구매,예약 관련 API")
public class PurchaseApiController {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseApiController.class);

    private final RedisQueueProducer redisQueueProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserService userService;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final PurchaseService purchaseService;

    @Autowired
    public PurchaseApiController(RedisQueueProducer redisQueueProducer, RedisTemplate<String, Object> redisTemplate, UserService userService, RedisService redisService, ObjectMapper objectMapper, PurchaseService purchaseService) {
        this.redisQueueProducer = redisQueueProducer;
        this.redisTemplate = redisTemplate;
        this.userService = userService;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.purchaseService = purchaseService;
    }

    @PostMapping("/buy")
    @Operation(summary = "구매(줄서기) 버튼 클릭", description = "구매의사를 가지고 버튼을 클릭합니다.")
    public ResponseEntity<Integer> buy(@RequestBody PurchaseRequest request) {
        try {
            // 구매 요청을 큐에 추가
            redisQueueProducer.sendPurchaseRequest(request);

            // 결과를 일정 시간 동안 폴링하여 조회
            int maxRetries = 10;  // 최대 10번 시도
            int retryInterval = 50; // 1초 간격으로 시도

            Integer result = null;
            for (int i = 0; i < maxRetries; i++) {
                result = (Integer) redisTemplate.opsForValue().get("purchaseResult:" + request.getUserId() + ":" + request.getProductId());
                if (result != null) {
                    break; // 결과를 성공적으로 가져온 경우
                }
                Thread.sleep(retryInterval); // 대기
            }

            if (result == null) {
                return ResponseEntity.ok(-1); // 아직 결과가 없는 경우
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing purchase request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(-1);
        }
    }

    @PostMapping("/batchBuy")
    public List<Boolean> batchBuy(@RequestBody List<PurchaseRequest> requests) {
        List<Boolean> results = new ArrayList<>();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            requests.forEach(request -> {
                try {
                    // 구매 요청 처리
                    purchaseService.processPurchaseRequest(request);
                    results.add(true); // 성공 시 true 추가
                } catch (Exception e) {
                    results.add(false); // 실패 시 false 추가
                }
            });
            return null;
        });
        return results;
    }



    @DeleteMapping("/cancel")
    @Operation(summary = "구매 취소 기능", description = "구매 취소를 합니다.")
    public ResponseEntity<?> cancel(@RequestBody PurchaseRequest request) {
        try {
            // 구매 취소 요청을 큐에 추가
            redisQueueProducer.sendCancelPurchaseRequest(request);

            // 결과를 일정 시간 동안 폴링하여 조회
            int maxRetries = 50;
            int retryInterval = 100;

            PurchaseCancleResponse result = null;
            String redisKey = "cancelPurchaseResult:" + request.getProductId();
            logger.info("redis key : {}", redisKey);

            for (int i = 0; i < maxRetries; i++) {
                String redisValue = (String) redisTemplate.opsForValue().get(redisKey);
                if (redisValue != null) {
                    logger.info("get redis key: {}", redisValue);

                    // Redis에서 가져온 JSON을 객체로 변환
                    result = objectMapper.readValue(redisValue, PurchaseCancleResponse.class);
                    break; // 결과를 성공적으로 가져온 경우
                } else {
                    logger.info("Redis key not found or null for key: {}", redisKey);
                }
                Thread.sleep(retryInterval); // 대기
            }

            if (result == null) {
                logger.warn("No cancel purchase result found for userId: {} and productId: {}", request.getUserId(), request.getProductId());
                return ResponseEntity.ok(-1); // 아직 결과가 없는 경우
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing cancel purchase request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(-1);
        }
    }

    @PostMapping("/reserve")
    @Operation(summary = "예약하기", description = "예약버튼이 활성화되어있을 시 예약합니다.")
    public ResponseEntity<Integer> reserve(@RequestBody PurchaseRequest request) {
        try {
            // 예약 요청을 큐에 추가
            redisQueueProducer.sendReservationRequest(request);

            // 결과를 일정 시간 동안 폴링하여 조회
            int maxRetries = 20;  // 최대 10번 시도
            int retryInterval = 50; // 1초 간격으로 시도

            Integer result = null;
            for (int i = 0; i < maxRetries; i++) {
                result = (Integer) redisTemplate.opsForValue().get("reservationResult:" + request.getUserId() + ":" + request.getProductId());
                if (result != null) {
                    break; // 결과를 성공적으로 가져온 경우
                }
                Thread.sleep(retryInterval); // 대기
            }

            if (result == null) {
                return ResponseEntity.ok(-1); // 아직 결과가 없는 경우
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing reservation request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(-1);
        }
    }

    @DeleteMapping("/reserve")
    @Operation(summary = "예약 취소하기", description = "예약을 취소합니다.")
    public ResponseEntity<Integer> cancelReservation(@RequestBody PurchaseRequest request) {
        try {
            // 예약 취소 요청을 큐에 추가
            redisQueueProducer.sendCancelReservationRequest(request);

            // 결과를 일정 시간 동안 폴링하여 조회
            int maxRetries = 20;  // 최대 10번 시도
            int retryInterval = 50; // 1초 간격으로 시도

            Integer result = null;
            for (int i = 0; i < maxRetries; i++) {
                result = (Integer) redisTemplate.opsForValue().get("cancelReservationResult:" + request.getUserId() + ":" + request.getProductId());
                if (result != null) {
                    break; // 결과를 성공적으로 가져온 경우
                }
                Thread.sleep(retryInterval); // 대기
            }

            if (result == null) {
                return ResponseEntity.ok(-1); // 아직 결과가 없는 경우
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing cancel reservation request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(-1);
        }
    }

    @PostMapping("/confirmPurchase")
    @Operation(summary = "구매 확정하기", description = "구매 예정자가 구매 시간 설정 후 구매를 확정합니다.")
    public ResponseEntity<?> confirmPurchase(@RequestBody TradeRequest request) {
        try {
            // 구매 확정 요청을 큐에 추가
            redisQueueProducer.sendConfirmPurchaseRequest(request);

            // 결과를 일정 시간 동안 폴링하여 조회
            int maxRetries = 10;  // 최대 10번 시도
            int retryInterval = 50; // 1초 간격으로 시도

            String result = null;
            for (int i = 0; i < maxRetries; i++) {
                result = (String) redisTemplate.opsForValue().get("confirmResult:" + request.getBuyerId() + ":" + request.getProductId());
                if (result != null) {
                    break; // 결과를 성공적으로 가져온 경우
                }
                Thread.sleep(retryInterval); // 대기
            }

            if (result == null) {
                logger.info("null here!!");
                return ResponseEntity.ok("not confirmed"); // 아직 결과가 없는 경우
            }
            System.out.println(result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing confirm request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing confirm request");
        }
    }

    @PostMapping("/confirmTrade")
    @Operation(summary = "거래 확정하기", description = "거래를 확정합니다.")
    public ResponseEntity<?> confirm(@RequestBody TradeRequest request) {
        try {
            // 구매 확정 요청을 큐에 추가
            redisQueueProducer.sendConfirmTradeRequest(request);

            // 결과를 일정 시간 동안 폴링하여 조회
            int maxRetries = 20;  // 최대 10번 시도
            int retryInterval = 50; // 1초 간격으로 시도

            String result = null;
            for (int i = 0; i < maxRetries; i++) {
                result = (String) redisTemplate.opsForValue().get("confirmResult:" + request.getBuyerId() + ":" + request.getProductId());
                if (result != null) {
                    break; // 결과를 성공적으로 가져온 경우
                }
                Thread.sleep(retryInterval); // 대기
            }

            if (result == null) {
                logger.info("null here!!");
                return ResponseEntity.ok("not confirmed"); // 아직 결과가 없는 경우
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing confirm request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing confirm request");
        }
    }

    // 거래 파기 요청 엔드포인트
    @DeleteMapping("/break-trade/{chatId}")
    @Operation(summary = "거래 파기", description = "특정 라이브의 모든 거래를 파기합니다.")
    public ResponseEntity<?> breakTrade(@AuthenticationPrincipal CustomOAuth2User principal, @PathVariable int chatId) {
        try {
            String userEmail = principal.getEmail(); // 현재 인증된 사용자의 이메일 가져오기

            User user = userService.findByEmail(userEmail); // 이메일로 userId 가져오기
            if (user == null) {
                return new ResponseEntity<>("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED);
            }

            // 거래 파기 요청을 큐에 추가
            redisQueueProducer.sendBreakTradeRequest(chatId, user.getUserId());

            // 결과를 일정 시간 동안 폴링하여 조회
            int maxRetries = 20;  // 최대 10번 시도
            int retryInterval = 50; // 1초 간격으로 시도

            List<PurchaseCancleResponse> result = null;
            String redisKey = "breakTradeResult:" + chatId + ":" + user.getUserId(); // 정확한 Redis 키 설정
            for (int i = 0; i < maxRetries; i++) {
                Object response = redisTemplate.opsForValue().get(redisKey);
                if (response != null && response instanceof List) {
                    result = (List<PurchaseCancleResponse>) response;
                    break; // 결과를 성공적으로 가져온 경우
                }
                Thread.sleep(retryInterval); // 대기
            }

            if (result == null) {
                return ResponseEntity.ok("아직 결과가 없습니다."); // 아직 결과가 없는 경우
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing break trade request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("거래 파기 중 오류 발생");
        }
    }

    @PutMapping("/reUpload")
    @Operation(summary = "끌어올리기", description = "쇼츠를 10% 할인된 가격으로 다시 끌어올립니다.")
    public ResponseEntity<?> reUpload(@RequestParam int productId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
            String userEmail = oAuth2User.getEmail(); // 현재 인증된 사용자의 이메일 가져오기

            User user = userService.findByEmail(userEmail); // 이메일로 사용자 정보를 가져옴
            if (user == null) {
                return new ResponseEntity<>("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED);
            }

            ShortsResponse result = purchaseService.reUpload(productId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing confirm request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing confirm request");
        }
    }

    private ResponseEntity<String> handleException(Exception ex) {
        return new ResponseEntity<>("서버 오류: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
