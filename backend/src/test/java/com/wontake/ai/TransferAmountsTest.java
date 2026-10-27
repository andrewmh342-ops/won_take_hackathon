package com.wontake.ai;

import com.wontake.api.ApiException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TransferAmountsTest {
    @Test void 금액은_서로_다른_참조로_가리고_원_단위로_복원한다() {
        var input = TransferAmounts.mask("월세 65만원, 적금 50만 원, 엄마 300,000원, 동생 1.5천원");
        assertEquals("월세 amount-1, 적금 amount-2, 엄마 amount-3, 동생 amount-4", input.text());
        assertEquals(List.of(650000L, 500000L, 300000L, 1500L), List.copyOf(input.values().values()));
    }
    @Test void 말하지_않은_금액은_만들지_않는다() {
        var input = TransferAmounts.mask("엄마한테 보내줘");
        assertTrue(input.values().isEmpty());
        assertEquals("엄마한테 보내줘", input.text());
    }
    @Test void 복합_단위와_음수의_일부분만_금액으로_읽지_않는다() {
        var input = TransferAmounts.mask("195만1원, 1억 5천만원, -500원");
        assertEquals("amount-1, amount-2, amount-3", input.text());
        assertEquals(List.of(1950001L, 150000000L, -500L), List.copyOf(input.values().values()));
    }
    @Test void 원_미만_값과_long_초과를_반올림하거나_빈_금액으로_바꾸지_않는다() {
        for (String value : List.of("0.5원", "9223372036854775808원"))
            assertThrows(ApiException.class, () -> TransferAmounts.mask(value));
    }
}
