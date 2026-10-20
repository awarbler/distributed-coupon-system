package coupons.api;

import com.sun.net.httpserver.*;
import coupons.*;
import coupons.repository.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Minimal authenticated HTTP API over the existing repository interfaces. */
public final class CouponApiService implements HttpHandler {
    private final TransactionRepository receipts;
    private final ShoppingPlanRepository plans;
    private final ClippedCouponRepository clippedCoupons;
    private final String token;
    private final String adminToken;
    private final Map<String, CachedResponse> idempotent = new ConcurrentHashMap<>();

    public CouponApiService(TransactionRepository receipts, ShoppingPlanRepository plans,
            ClippedCouponRepository clippedCoupons, String token, String adminToken) {
        this.receipts = Objects.requireNonNull(receipts);
        this.plans = Objects.requireNonNull(plans);
        this.clippedCoupons = Objects.requireNonNull(clippedCoupons);
        this.token = Objects.requireNonNull(token);
        this.adminToken = adminToken;
    }
    public static CouponApiService fromEnvironment(TransactionRepository receipts,
            ShoppingPlanRepository plans, ClippedCouponRepository coupons) {
        String token = System.getenv("COUPON_API_TOKEN");
        if (token == null || token.isBlank()) throw new IllegalStateException("COUPON_API_TOKEN is required.");
        return new CouponApiService(receipts, plans, coupons, token, System.getenv("COUPON_API_ADMIN_TOKEN"));
    }
    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return server;
    }
    @Override public void handle(HttpExchange exchange) throws IOException {
        try {
            dispatch(exchange);
        } catch (Throwable error) {
            int status = error instanceof SecurityException ? 401
                    : error instanceof NoSuchElementException ? 404
                    : error instanceof ConcurrentModificationException || error instanceof IllegalStateException ? 409
                    : error instanceof IllegalArgumentException ? 400 : 500;
            send(exchange, status, Json.object(Map.of("error", message(error))));
        }
    }
    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/health".equals(path)) { send(exchange, 200, Json.object(Map.of("status", "ok"))); return; }
        boolean write = !"GET".equalsIgnoreCase(exchange.getRequestMethod());
        String actor = authorize(exchange, write);
        String[] parts = path.split("/");
        if (parts.length == 3 && "receipts".equals(parts[1]) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            ReceiptRecord receipt = receipts.findById(UUID.fromString(parts[2]))
                    .orElseThrow(() -> new NoSuchElementException("Receipt not found."));
            send(exchange, 200, Json.object(Map.of("transactionId", receipt.transactionId(),
                    "revision", receipt.revision(), "status", receipt.status().name())));
            return;
        }
        if (parts.length == 3 && "shopping-plans".equals(parts[1])) {
            UUID id = UUID.fromString(parts[2]);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                ShoppingPlan plan = plans.findById(id).orElseThrow(() -> new NoSuchElementException("Plan not found."));
                send(exchange, 200, planJson(plan)); return;
            }
            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                String request = body(exchange); CachedResponse prior = replay(exchange, request);
                if (prior != null) { send(exchange, prior.status(), prior.body()); return; }
                ShoppingPlan updated = plans.update(readPlan(id, request), revision(exchange), actor, reason(exchange));
                String response = planJson(updated); remember(exchange, request, 200, response); send(exchange, 200, response); return;
            }
        }
        if ("/shopping-plans".equals(path) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String request = body(exchange); CachedResponse prior = replay(exchange, request);
            if (prior != null) { send(exchange, prior.status(), prior.body()); return; }
            ShoppingPlan plan = readPlan(null, request); plans.save(plan, actor);
            String response = planJson(plan); remember(exchange, request, 201, response); send(exchange, 201, response); return;
        }
        if (parts.length == 4 && "clipped-coupons".equals(parts[1]) && "redeem".equals(parts[3])
                && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String request = body(exchange); CachedResponse prior = replay(exchange, request);
            if (prior != null) { send(exchange, prior.status(), prior.body()); return; }
            Map<String, String> fields = Json.parseObject(request);
            ClippedCoupon coupon = clippedCoupons.redeem(UUID.fromString(parts[2]),
                    UUID.fromString(required(fields, "transactionId")),
                    LocalDate.parse(fields.getOrDefault("redeemedOn", LocalDate.now().toString())),
                    revision(exchange), actor);
            String response = couponJson(coupon); remember(exchange, request, 200, response); send(exchange, 200, response); return;
        }
        if (parts.length == 2 && "clipped-coupons".equals(parts[1])
                && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String provider = query(exchange.getRequestURI(), "provider");
            if (provider == null) throw new IllegalArgumentException("provider query parameter is required.");
            send(exchange, 200, Json.array(clippedCoupons.findByProvider(provider).stream().map(CouponApiService::couponJson).toList()));
            return;
        }
        throw new NoSuchElementException("Unknown endpoint.");
    }
    private ShoppingPlan readPlan(UUID pathId, String request) {
        Map<String, String> fields = Json.parseObject(request);
        UUID id = pathId == null ? UUID.fromString(required(fields, "planId")) : pathId;
        String date = fields.get("shoppingDate");
        return new ShoppingPlan(id, required(fields, "name"), fields.get("retailer"),
                date == null ? null : LocalDate.parse(date), List.of(),
                Long.parseLong(fields.getOrDefault("revision", pathId == null ? "1" : "2")));
    }
    private String authorize(HttpExchange exchange, boolean write) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        boolean normal = ("Bearer " + token).equals(header);
        boolean admin = adminToken != null && !adminToken.isBlank() && ("Bearer " + adminToken).equals(header);
        if (!normal && !admin) throw new SecurityException("Bearer token required.");
        if (write && adminToken != null && !adminToken.isBlank() && !admin)
            throw new SecurityException("Write authorization required.");
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Actor"))
                .filter(value -> !value.isBlank()).orElse("api");
    }
    private static String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }
    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required.");
        return value;
    }
    private static long revision(HttpExchange exchange) {
        return Long.parseLong(Optional.ofNullable(exchange.getRequestHeaders().getFirst("If-Match-Revision")).orElse("1"));
    }
    private static String reason(HttpExchange exchange) {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Reason"))
                .filter(value -> !value.isBlank()).orElse("API update");
    }
    private static String query(URI uri, String key) {
        if (uri.getRawQuery() == null) return null;
        for (String part : uri.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair[0].equals(key)) return URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8);
        }
        return null;
    }
    private record CachedResponse(int status, String body, String hash) {}
    private CachedResponse replay(HttpExchange exchange, String request) {
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        if (key == null || key.isBlank()) return null;
        CachedResponse cached = idempotent.get(key);
        if (cached != null && !cached.hash().equals(Integer.toHexString(request.hashCode())))
            throw new IllegalArgumentException("Idempotency key was reused with different content.");
        return cached;
    }
    private void remember(HttpExchange exchange, String request, int status, String response) {
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        if (key != null && !key.isBlank())
            idempotent.putIfAbsent(key, new CachedResponse(status, response, Integer.toHexString(request.hashCode())));
    }
    private static String planJson(ShoppingPlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("planId", plan.planId()); values.put("name", plan.name()); values.put("retailer", plan.retailer());
        values.put("shoppingDate", plan.shoppingDate()); values.put("revision", plan.revision());
        values.put("plannedPurchaseIds", plan.plannedPurchaseIds()); return Json.object(values);
    }
    private static String couponJson(ClippedCoupon coupon) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("couponId", coupon.couponId()); values.put("provider", coupon.provider());
        values.put("externalId", coupon.externalId()); values.put("status", coupon.status());
        values.put("clippedOn", coupon.clippedOn()); values.put("redeemedOn", coupon.redeemedOn());
        values.put("redeemedTransactionId", coupon.redeemedTransactionId()); values.put("revision", coupon.revision());
        return Json.object(values);
    }
    private static String message(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static void send(HttpExchange exchange, int status, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
