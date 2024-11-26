//package com.ssafy.fleaOn.web.consumer;
//
//import com.ssafy.fleaOn.web.dto.PurchaseCancleResponse;
//import com.ssafy.fleaOn.web.dto.PurchaseRequest;
//import com.ssafy.fleaOn.web.dto.TradeRequest;
//import com.ssafy.fleaOn.web.service.PurchaseService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//public class RedisQueueConsumer {
//
//    private static final Logger logger = LoggerFactory.getLogger(RedisQueueConsumer.class);
//
//    @Autowired
//    private RedisTemplate<String, Object> redisTemplate;
//
//    @Autowired
//    private PurchaseService purchaseService;
//
//    @Scheduled(fixedDelay = 1000)
//    public void handlePurchaseRequest() {
//        PurchaseRequest request = (PurchaseRequest) redisTemplate.opsForList().leftPop("purchaseQueue");
//        if (request != null) {
//            int result = purchaseService.processPurchaseRequest(request);
//            redisTemplate.opsForValue().set("purchaseResult:" + request.getUserId() + ":" + request.getProductId(), result);
//        }
//    }
//
//    @Scheduled(fixedDelay = 1000)
//    public void handleCancelPurchaseRequest() {
//        PurchaseRequest request = (PurchaseRequest) redisTemplate.opsForList().leftPop("cancelPurchaseQueue");
//        if (request != null) {
//            PurchaseCancleResponse result = purchaseService.cancelPurchaseProduct(request);
//            redisTemplate.opsForValue().set("cancelPurchaseResult:" + request.getUserId() + ":" + request.getProductId(), result);
//        }
//    }
//
//    @Scheduled(fixedDelay = 1000)
//    public void handleReservationRequest() {
//        PurchaseRequest request = (PurchaseRequest) redisTemplate.opsForList().leftPop("reservationQueue");
//        if (request != null) {
//            int result = purchaseService.processReservationRequest(request);
//            redisTemplate.opsForValue().set("reservationResult:" + request.getUserId() + ":" + request.getProductId(), result);
//        }
//    }
//
//    @Scheduled(fixedDelay = 1000)
//    public void handleCancelReservationRequest() {
//        PurchaseRequest request = (PurchaseRequest) redisTemplate.opsForList().leftPop("cancelReservationQueue");
//        if (request != null) {
//            int result = purchaseService.processCancelReservationRequest(request);
//            redisTemplate.opsForValue().set("cancelReservationResult:" + request.getUserId() + ":" + request.getProductId(), result);
//        }
//    }
//
//    @Scheduled(fixedDelay = 1000)
//    public void handleConfirmPurchaseRequest() {
//        TradeRequest request = (TradeRequest) redisTemplate.opsForList().leftPop("confirmPurchaseQueue");
//        if (request != null) {
//            purchaseService.processConfirmPurchaseRequest(request);
//            redisTemplate.opsForValue().set("confirmResult:" + request.getBuyerId() + ":" + request.getProductId(), "confirmed");
//        }
//    }
//
//    @Scheduled(fixedDelay = 1000)
//    public void handleConfirmTradeRequest() {  // 메서드명 수정
//        TradeRequest request = (TradeRequest) redisTemplate.opsForList().leftPop("confirmTradeQueue");
//        if (request != null) {
//            purchaseService.processConfirmTradeRequest(request);
//            redisTemplate.opsForValue().set("confirmResult:" + request.getBuyerId() + ":" + request.getProductId(), "confirmed");
//        }
//    }
//
//    @Scheduled(fixedDelay = 1000)
//    public void handleBreakTradeRequest() {
//        int[] chatAndUserId = (int[]) redisTemplate.opsForList().leftPop("breakTradeQueue");
//        if (chatAndUserId != null && chatAndUserId.length == 2) {
//            int chatId = chatAndUserId[0];
//            int userId = chatAndUserId[1];
//            logger.info("Received BreakTradeRequest for chatId: {} and userId: {}", chatId, userId);
//
//            List<PurchaseCancleResponse> result = purchaseService.breakTrade(chatId, userId);
//            logger.info("Processed BreakTradeRequest with result: {}", result);
//
//            redisTemplate.opsForValue().set("breakTradeResult:" + chatId + ":" + userId, result);  // key 수정
//        } else {
//            logger.info("No BreakTradeRequest found in queue");
//        }
//    }
//}
package com.ssafy.fleaOn.web.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fleaOn.web.dto.PurchaseCancleResponse;
import com.ssafy.fleaOn.web.dto.PurchaseRequest;
import com.ssafy.fleaOn.web.dto.TradeRequest;
import com.ssafy.fleaOn.web.service.PurchaseService;
import com.ssafy.fleaOn.web.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.ssafy.fleaOn.web.producer.RedisQueueProducer.*;

@Service
public class RedisQueueConsumer {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueueConsumer.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisService redisService;

//    @Scheduled(fixedDelay = 500)
//    public void handlePurchaseRequest() {
//        Object data = redisTemplate.opsForList().leftPop(PURCHASE_QUEUE);
//        if (data != null) {
//            PurchaseRequest request = objectMapper.convertValue(data, PurchaseRequest.class);
//            int result = purchaseService.processPurchaseRequest(request);
//            redisTemplate.opsForValue().set("purchaseResult:" + request.getUserId() + ":" + request.getProductId(), result);
//        }
//    }
    @Scheduled(fixedDelay = 500)
    public void handlePurchaseRequests() {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // Redis 리스트에서 데이터 가져오기
            List<Object> requests = redisTemplate.opsForList().range(PURCHASE_QUEUE, 0, -1);

            if (requests != null) {
                for (Object data : requests) {
                    try {
                        // LinkedHashMap 데이터를 PurchaseRequest로 변환
                        PurchaseRequest request = objectMapper.convertValue(data, PurchaseRequest.class);

                        // 서비스 로직 호출
                        int result = purchaseService.processPurchaseRequest(request);

                        // 결과를 Redis에 저장
                        redisTemplate.opsForValue().set("purchaseResult:" + request.getUserId() + ":" + request.getProductId(), result);
                    } catch (IllegalArgumentException e) {
                        logger.error("Failed to convert data to PurchaseRequest: {}", data, e);
                    }
                }
            }

            // 처리 완료 후 큐 비우기
            redisTemplate.opsForList().trim(PURCHASE_QUEUE, 1, 0);
            return null;
        });
    }


    @Scheduled(fixedDelay = 500)
    public void handleCancelPurchaseRequest() throws JsonProcessingException {
        Object data = redisTemplate.opsForList().leftPop(CANCEL_PURCHASE_QUEUE);
        if (data != null) {
            PurchaseRequest request = objectMapper.convertValue(data, PurchaseRequest.class);
            logger.info("user id: {}", request.getUserId());
            PurchaseCancleResponse result = purchaseService.cancelPurchaseProduct(request);
            redisService.setRedisValue(result);
        }
    }

    @Scheduled(fixedDelay = 500)
    public void handleReservationRequest() {
        Object data = redisTemplate.opsForList().leftPop(RESERVATION_QUEUE);
        if (data != null) {
            PurchaseRequest request = objectMapper.convertValue(data, PurchaseRequest.class);
            int result = purchaseService.processReservationRequest(request);
            redisTemplate.opsForValue().set("reservationResult:" + request.getUserId() + ":" + request.getProductId(), result);
        }
    }

    @Scheduled(fixedDelay = 500)
    public void handleCancelReservationRequest() {
        Object data = redisTemplate.opsForList().leftPop(CANCEL_RESERVATION_QUEUE);
        if (data != null) {
            PurchaseRequest request = objectMapper.convertValue(data, PurchaseRequest.class);
            int result = purchaseService.processCancelReservationRequest(request);
            redisTemplate.opsForValue().set("cancelReservationResult:" + request.getUserId() + ":" + request.getProductId(), result);
        }
    }

    @Scheduled(fixedDelay = 500)
    public void handleConfirmPurchaseRequest() {
        Object data = redisTemplate.opsForList().leftPop(CONFIRM_PURCHASE_QUEUE);
        if (data != null) {
            TradeRequest request = objectMapper.convertValue(data, TradeRequest.class);
            logger.info(String.valueOf(request.getProductId()));
            String result = purchaseService.processConfirmPurchaseRequest(request);
            redisTemplate.opsForValue().set("confirPurchasemResult:" + request.getBuyerId() + ":" + request.getProductId(), result);
        }
    }

    @Scheduled(fixedDelay = 500)
    public void handleConfirmTradeRequest() {
        Object data = redisTemplate.opsForList().leftPop(CONFIRM_TRADE_QUEUE);
        if (data != null) {
            TradeRequest request = objectMapper.convertValue(data, TradeRequest.class);
            purchaseService.processConfirmTradeRequest(request);
            redisTemplate.opsForValue().set("confirmTradeResult:" + request.getBuyerId() + ":" + request.getProductId(), "confirmed");
        }
    }

    @Scheduled(fixedDelay = 500)
    public void handleBreakTradeRequest() {
        Object data = redisTemplate.opsForList().leftPop(BREAK_TRADE_QUEUE);
//        logger.info("Data popped from queue: {}", data);
        if (data instanceof List) {
            List<Integer> dataList = (List<Integer>) data;
            int chatId = dataList.get(0);
            int userId = dataList.get(1);
            logger.info("Processing BreakTradeRequest for chatId: " + chatId + " and userId: " + userId);

            List<PurchaseCancleResponse> result = purchaseService.breakTrade(chatId, userId);
            logger.info("Processed BreakTradeRequest with result: {}", result);

            String redisKey = "breakTradeResult:" + chatId + ":" + userId; // Redis 키 정확히 설정
            redisTemplate.opsForValue().set(redisKey, result);
        } else {
//            logger.info("No BreakTradeRequest found in queue");
        }
    }

}
