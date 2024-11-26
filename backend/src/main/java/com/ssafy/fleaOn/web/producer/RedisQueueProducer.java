package com.ssafy.fleaOn.web.producer;

import com.ssafy.fleaOn.web.consumer.RedisQueueConsumer;
import com.ssafy.fleaOn.web.dto.PurchaseRequest;
import com.ssafy.fleaOn.web.dto.TradeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedisQueueProducer {

//    private static final Logger logger = LoggerFactory.getLogger(RedisQueueConsumer.class);

    // Redis 큐 이름 상수 정의
    public static final String PURCHASE_QUEUE = "purchaseQueue";
    public static final String CANCEL_PURCHASE_QUEUE = "cancelPurchaseQueue";
    public static final String RESERVATION_QUEUE = "reservationQueue";
    public static final String CANCEL_RESERVATION_QUEUE = "cancelReservationQueue";
    public static final String CONFIRM_PURCHASE_QUEUE = "confirmPurchaseQueue";
    public static final String CONFIRM_TRADE_QUEUE = "confirmTradeQueue";
    public static final String BREAK_TRADE_QUEUE = "breakTradeQueue"; // 거래 파기 큐 추가

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 대량의 구매 요청을 큐에 추가하는 메서드 (파이프라이닝 적용)
    public void sendPurchaseRequests(List<PurchaseRequest> requests) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (PurchaseRequest request : requests) {
                redisTemplate.opsForList().rightPush(PURCHASE_QUEUE, request);
            }
            return null;
        });
    }

    // 구매 요청을 큐에 추가하는 메서드
    public void sendPurchaseRequest(PurchaseRequest request) {
        redisTemplate.opsForList().rightPush(PURCHASE_QUEUE, request);
    }

    // 구매 취소 요청을 큐에 추가하는 메서드
    public void sendCancelPurchaseRequest(PurchaseRequest request) {
        redisTemplate.opsForList().rightPush(CANCEL_PURCHASE_QUEUE, request);
    }

    // 예약 요청을 큐에 추가하는 메서드
    public void sendReservationRequest(PurchaseRequest request) {
        redisTemplate.opsForList().rightPush(RESERVATION_QUEUE, request);
    }

    // 예약 취소 요청을 큐에 추가하는 메서드
    public void sendCancelReservationRequest(PurchaseRequest request) {
        redisTemplate.opsForList().rightPush(CANCEL_RESERVATION_QUEUE, request);
    }

    // 구매 확정 요청을 큐에 추가하는 메서드
    public void sendConfirmPurchaseRequest(TradeRequest request) {
        redisTemplate.opsForList().rightPush(CONFIRM_PURCHASE_QUEUE, request);
    }

    // 거래 파기 요청을 큐에 추가하는 메서드
    public void sendBreakTradeRequest(int chatId, int userId) {
//        logger.info("Pushing BreakTradeRequest to Redis queue: chatId={}, userId={}", chatId, userId);
        redisTemplate.opsForList().rightPush(BREAK_TRADE_QUEUE, new int[]{chatId, userId});
    }


    public void sendConfirmTradeRequest(TradeRequest request) {
        redisTemplate.opsForList().rightPush(CONFIRM_TRADE_QUEUE, request);
    }
}
