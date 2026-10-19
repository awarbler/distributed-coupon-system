package coupons.api;

import com.sun.net.httpserver.*;
import coupons.ClippedCoupon;
import coupons.ShoppingPlan;
import coupons.repository.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated HTTP adapter over the existing repositories. */
public final class CouponApiService implements HttpHandler {
    private final TransactionRepository receipts;
    private final ShoppingPlanRepository plans;
    private final ClippedCouponRepository coupons;
    private final String token;
    private final String adminToken;
    private final Map<String, Cached> idempotency = new ConcurrentHashMap<>();
    public CouponApiService(TransactionRepository receipts, ShoppingPlanRepository plans,
            ClippedCouponRepository coupons, String token, String adminToken) {
        this.receipts = Objects.requireNonNull(receipts); this.plans = Objects.requireNonNull(plans);
        this.coupons = Objects.requireNonNull(coupons); this.token = Objects.requireNonNull(token);
        this.adminToken = adminToken;
    }
    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this); server.setExecutor(Executors.newCachedThreadPool()); server.start(); return server;
    }
    @Override public void handle(HttpExchange exchange) throws IOException {
        try { route(exchange); } catch (Throwable error) {
            int status = error instanceof SecurityException ? 401 : error instanceof NoSuchElementException ? 404
                    : error instanceof ConcurrentModificationException || error instanceof IllegalStateException ? 409
                    : error instanceof IllegalArgumentException ? 400 : 500;
            send(exchange, status, Json.object(Map.of("error", error.getMessage() == null ? "request failed" : error.getMessage())));
        }
    }
    private void route(HttpExchange e) throws IOException {
        String path = e.getRequestURI().getPath();
        if ("/health".equals(path)) { send(e, 200, Json.object(Map.of("status", "ok"))); return; }
        boolean write = !"GET".equalsIgnoreCase(e.getRequestMethod());
        String actor = authorize(e, write);
        String[] parts = path.split("/");
        if (parts.length == 3 && "receipts".equals(parts[1]) && "GET".equalsIgnoreCase(e.getRequestMethod())) {
            ReceiptRecord r = receipts.findById(UUID.fromString(parts[2])).orElseThrow(() -> new NoSuchElementException("Receipt not found."));
            send(e, 200, Json.object(Map.of("transactionId", r.transactionId(), "revision", r.revision(), "status", r.status().name()))); return;
        }
        if ("/shopping-plans".equals(path) && "POST".equalsIgnoreCase(e.getRequestMethod())) {
            String request = body(e); Cached old = replay(e, request); if (old != null) { send(e, old.status, old.body); return; }
            ShoppingPlan plan = parsePlan(null, request); plans.save(plan, actor); String response = planJson(plan);
            remember(e, request, 201, response); send(e, 201, response); return;
        }
        if (parts.length == 3 && "shopping-plans".equals(parts[1])) {
            UUID id = UUID.fromString(parts[2]);
            if ("GET".equalsIgnoreCase(e.getRequestMethod())) {
                send(e, 200, planJson(plans.findById(id).orElseThrow(() -> new NoSuchElementException("Plan not found.")))); return;
            }
            if ("PUT".equalsIgnoreCase(e.getRequestMethod())) {
                String request = body(e); Cached old = replay(e, request); if (old != null) { send(e, old.status, old.body); return; }
                ShoppingPlan plan = plans.update(parsePlan(id, request), revision(e), actor, "API update");
                String response = planJson(plan); remember(e, request, 200, response); send(e, 200, response); return;
            }
        }
        if (parts.length == 2 && "clipped-coupons".equals(parts[1]) && "GET".equalsIgnoreCase(e.getRequestMethod())) {
            String provider = query(e.getRequestURI(), "provider");
            if (provider == null) throw new IllegalArgumentException("provider query parameter is required.");
            send(e, 200, Json.array(coupons.findByProvider(provider).stream().map(CouponApiService::couponJson).toList())); return;
        }
        if (parts.length == 4 && "clipped-coupons".equals(parts[1]) && "redeem".equals(parts[3])
                && "POST".equalsIgnoreCase(e.getRequestMethod())) {
            String request = body(e); Cached old = replay(e, request); if (old != null) { send(e, old.status, old.body); return; }
            Map<String, String> fields = Json.objectFields(request);
            ClippedCoupon coupon = coupons.redeem(UUID.fromString(parts[2]), UUID.fromString(required(fields, "transactionId")),
                    LocalDate.parse(fields.getOrDefault("redeemedOn", LocalDate.now().toString())), revision(e), actor);
            String response = couponJson(coupon); remember(e, request, 200, response); send(e, 200, response); return;
        }
        throw new NoSuchElementException("Unknown endpoint.");
    }
    private ShoppingPlan parsePlan(UUID pathId, String request) {
        Map<String, String> fields = Json.objectFields(request);
        UUID id = pathId == null ? UUID.fromString(required(fields, "planId")) : pathId;
        String date = fields.get("shoppingDate");
        return new ShoppingPlan(id, required(fields, "name"), fields.get("retailer"),
                date == null ? null : LocalDate.parse(date), List.of(),
                Long.parseLong(fields.getOrDefault("revision", pathId == null ? "1" : "2")));
    }
    private String authorize(HttpExchange e, boolean write) {
        String supplied = e.getRequestHeaders().getFirst("Authorization");
        boolean normal = ("Bearer " + token).equals(supplied);
        boolean admin = adminToken != null && !adminToken.isBlank() && ("Bearer " + adminToken).equals(supplied);
        if (!normal && !admin) throw new SecurityException("Bearer token required.");
        if (write && adminToken != null && !adminToken.isBlank() && !admin) throw new SecurityException("Write authorization required.");
        return Optional.ofNullable(e.getRequestHeaders().getFirst("X-Actor")).filter(s -> !s.isBlank()).orElse("api");
    }
    private static String body(HttpExchange e) throws IOException { return new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); }
    private static String required(Map<String, String> fields, String key) { String value = fields.get(key); if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required."); return value; }
    private static long revision(HttpExchange e) { return Long.parseLong(Optional.ofNullable(e.getRequestHeaders().getFirst("If-Match-Revision")).orElse("1")); }
    private static String query(URI uri, String key) { if (uri.getRawQuery() == null) return null; for (String p : uri.getRawQuery().split("&")) { String[] a = p.split("=", 2); if (a[0].equals(key)) return a.length == 2 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : ""; } return null; }
    private record Cached(int status, String body, String hash) {}
    private Cached replay(HttpExchange e, String request) { String key = e.getRequestHeaders().getFirst("Idempotency-Key"); if (key == null || key.isBlank()) return null; Cached cached = idempotency.get(key); if (cached != null && !cached.hash.equals(Integer.toHexString(request.hashCode()))) throw new IllegalArgumentException("Idempotency key reused with different content."); return cached; }
    private void remember(HttpExchange e, String request, int status, String response) { String key = e.getRequestHeaders().getFirst("Idempotency-Key"); if (key != null && !key.isBlank()) idempotency.putIfAbsent(key, new Cached(status, response, Integer.toHexString(request.hashCode()))); }
    private static String planJson(ShoppingPlan p) { return Json.object(new LinkedHashMap<>(Map.of("planId", p.planId(), "name", p.name(), "retailer", p.retailer(), "shoppingDate", p.shoppingDate(), "revision", p.revision(), "plannedPurchaseIds", p.plannedPurchaseIds()))); }
    private static String couponJson(ClippedCoupon c) { return Json.object(new LinkedHashMap<>(Map.of("couponId", c.couponId(), "provider", c.provider(), "externalId", c.externalId(), "status", c.status(), "clippedOn", c.clippedOn(), "redeemedOn", c.redeemedOn(), "redeemedTransactionId", c.redeemedTransactionId(), "revision", c.revision()))); }
    private static void send(HttpExchange e, int status, String response) throws IOException { e.getResponseHeaders().set("Content-Type", "application/json"); byte[] bytes = response.getBytes(StandardCharsets.UTF_8); e.sendResponseHeaders(status, bytes.length); try (OutputStream out = e.getResponseBody()) { out.write(bytes); } }
}
