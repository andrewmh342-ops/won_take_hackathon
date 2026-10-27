package com.wontake.catalog;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuCatalogTest {
    @Test void 모든_메뉴에_AI가_쓸_안내_근거가_있다() {
        var catalog = new MenuCatalog();
        assertEquals(28, catalog.all().size());
        for (var m : catalog.all()) {
            assertFalse(m.description().contains("이동하는 시연 화면"), m.id() + " 설명이 기본 문구");
            assertTrue(m.guide().size() >= 2 && m.guide().size() <= 4, m.id() + " guide 개수");
            assertTrue(m.commonIssues().size() >= 1 && m.commonIssues().size() <= 3, m.id() + " commonIssues 개수");
            assertTrue(m.guide().stream().noneMatch(String::isBlank));
        }
    }

    @Test void 관련_메뉴는_실제_목록의_다른_메뉴다() {
        var catalog = new MenuCatalog();
        for (var m : catalog.all())
            for (var id : m.relatedMenuIds()) {
                assertTrue(catalog.contains(id), m.id() + " → " + id);
                assertNotEquals(m.id(), id);
            }
    }
}
