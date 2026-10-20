package coupons.api;

import com.sun.net.httpserver.HttpServer;
import coupons.repository.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CouponApiServiceTest {
    @SuppressWarnings("unchecked")
    private static <T> T emptyRepository(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            if (method.getReturnType() == Optional.class) return Optional.empty();
            if (method.getReturnType() == List.class) return List.of();
            return null;
        });
    }
    @Test
    void healthIsPublicAndReturnsJson() throws Exception {
        CouponApiService api = new CouponApiService(emptyRepository(TransactionRepository.class),
                emptyRepository(ShoppingPlanRepository.class), emptyRepository(ClippedCouponRepository.class), "secret", null);
        HttpServer server = api.start(0);
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + server.getAddress().getPort() + "/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("{\"status\":\"ok\"}", response.body());
        } finally { server.stop(0); }
    }
}
