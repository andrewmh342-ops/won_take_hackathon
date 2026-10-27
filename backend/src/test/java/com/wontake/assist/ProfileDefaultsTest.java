package com.wontake.assist;

import com.wontake.session.DemoStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 작업 34: 시연 고객은 모두 해외결제 꺼짐으로 시작한다(상황에 맞게 켜는 장면을 보여 주기 위해). */
class ProfileDefaultsTest {
    @Test void 시연_고객의_모든_카드는_해외결제가_꺼져_있다() {
        var profiles = new ProfileStore(new DemoStore());
        for (var id : List.of("A", "B", "C")) {
            profiles.select("u", "s-" + id, id);
            for (var card : profiles.state("u", "s-" + id).cards)
                assertFalse(card.overseasPayment, id + " " + card.ref);
        }
    }
}
