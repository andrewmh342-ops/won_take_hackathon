package com.wontake.assist;

import java.util.*;

/** R5 원테이크 플랜. 화면에 그대로 내려가며 실행할 때마다 상태가 갱신된다. */
public class Plan {
    public static class PlanBlock {
        public int index;
        public String blockId, name, type, menuId, targetRef, target, param, reason;
        public String status = "PENDING"; // PENDING | DONE | VISITED
        public String priority = "REQUIRED"; // REQUIRED | OPTIONAL (R5 목표 중심)
        public boolean adjusted;
        public String detail = "";
        public List<Map<String, Object>> items = List.of();
    }
    public String planId;
    public List<String> events;
    public String orderReason;
    public List<PlanBlock> blocks;
    public List<String> unhandled;
    /** AI에 보낸 가명화된 입력(시연 투명성용). */
    public Object aiInput;
    // R5 목표 중심 판단
    /** AI가 정한 목표: {type, text, required}. */
    public List<Map<String, Object>> goals = List.of();
    /** 서버가 가상 상태로 판정한 목표 상태(블록 완료와 별개). */
    public List<GoalCheck.GoalStatus> goalStatus = List.of();
    /** AI가 찾은 장애물: {text, resolution, blockIndexes}. */
    public List<Map<String, Object>> obstacles = List.of();
    /** AI가 넣지 않은 업무와 이유: {blockId, name, reason}. */
    public List<Map<String, Object>> considered = List.of();
    /** 서버가 뺀 업무와 이유: {blockId, name, target, reason}. */
    public List<Map<String, Object>> skipped = List.of();
    /** 필수 블록이 끝났고 필수 목표가 모두 확보됐는지. */
    public boolean complete;
    /** 아직 남은 문제(확보되지 않은 필수 목표, 상담 필요). */
    public List<String> remaining = List.of();
    // 분실 카드 직접 확인(작업 23)
    public boolean needsCardConfirm;
    public List<String> suggestedCards = List.of();
    public List<String> confirmedCards;
    /** 재판단 결과 안내(예: AI가 다시 판단하지 못함). 없으면 "". */
    public String notice = "";
    /** 재판단에 쓰는 원래 질문(원문). 화면에는 내려가지 않는다. */
    @com.fasterxml.jackson.annotation.JsonIgnore public String question = "";
    /** 마지막으로 조립에 쓴 AI 플랜(카드 확인 후 다시 구성할 때 쓴다). 화면에는 내려가지 않는다. */
    @com.fasterxml.jackson.annotation.JsonIgnore public com.wontake.ai.AiClient.AiPlan source;
}
