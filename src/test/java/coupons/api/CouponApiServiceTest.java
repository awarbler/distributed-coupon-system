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
    private static <T> T repository(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (o, m, a) ->
                m.getReturnType() == Optional.class ? Optional.empty() : m.getReturnType() == List.class ? List.of() : null);
    }
    @Test void healthIsPublic() throws Exception {
        CouponApiService api = new CouponApiService(repository(TransactionRepository.class), repository(ShoppingPlanRepository.class), repository(ClippedCouponRepository.class), "secret", null);
        HttpServer server = api.start(0);
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.getAddress().getPort() + "/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode()); assertEquals("{\"status\":\"ok\"}", response.body());
        } finally { server.stop(0); }
    }
}
