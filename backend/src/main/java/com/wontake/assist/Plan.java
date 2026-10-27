package com.wontake.assist;

import java.util.*;

/** R5 원테이크 플랜. 화면에 그대로 내려가며 실행할 때마다 상태가 갱신된다. */
public class Plan {
    public static class PlanBlock {
        public int index;
        public String blockId, name, type, menuId, targetRef, target, param, reason;
        public String status = "PENDING"; // PENDING | DONE | VISITED
        public boolean adjusted;
        public String detail = "";
        public List<Map<String, Object>> items = List.of();
    }
    public String planId;
    public List<String> events;
    public String orderReason;
    public List<PlanBlock> blocks;
    public List<String> unhandled;
    /** AI에 보낸 가명화된 모양(시연 투명성용). */
    public Object aiInput;
}
