package com.interviewai.service;

import com.interviewai.dto.PaymentOrderResponse;
import com.interviewai.dto.PaymentVerifyRequest;
import com.interviewai.entity.Subscription;
import com.interviewai.entity.User;
import com.interviewai.repository.SubscriptionRepository;
import com.interviewai.repository.UserRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Formatter;
import java.util.List;

@Service
public class PaymentService {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    private static final int PRO_AMOUNT_PAISE = 29900; // ₹299

    public PaymentOrderResponse createOrder(User user) throws Exception {
        RazorpayClient client = new RazorpayClient(keyId, keySecret);

        JSONObject options = new JSONObject();
        options.put("amount", PRO_AMOUNT_PAISE);
        options.put("currency", "INR");
        options.put("receipt", "rcpt_" + user.getId() + "_" + System.currentTimeMillis());
        options.put("payment_capture", 1);

        Order order = client.orders.create(options);

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setRazorpayOrderId(order.get("id").toString());
        sub.setAmountPaise(PRO_AMOUNT_PAISE);
        sub.setPlan("PRO");
        sub.setStatus("CREATED");
        subscriptionRepository.save(sub);

        PaymentOrderResponse response = new PaymentOrderResponse();
        response.setOrderId(order.get("id").toString());
        response.setAmount(PRO_AMOUNT_PAISE);
        response.setCurrency("INR");
        response.setKeyId(keyId);
        return response;
    }

    public boolean verifyAndActivate(User user, PaymentVerifyRequest req) throws Exception {
        String data = req.getRazorpayOrderId() + "|" + req.getRazorpayPaymentId();
        String generatedSignature = hmacSHA256(data, keySecret);

        if (!generatedSignature.equals(req.getRazorpaySignature())) {
            return false;
        }

        Subscription sub = subscriptionRepository
                .findByRazorpayOrderId(req.getRazorpayOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        sub.setRazorpayPaymentId(req.getRazorpayPaymentId());
        sub.setStatus("PAID");
        sub.setPaidAt(LocalDateTime.now());
        subscriptionRepository.save(sub);

        user.setPlan("PRO");
        userRepository.save(user);

        return true;
    }

    public List<Subscription> getHistory(User user) {
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    private String hmacSHA256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}
