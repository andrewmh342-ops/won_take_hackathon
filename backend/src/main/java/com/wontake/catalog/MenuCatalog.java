package com.wontake.catalog;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.Arrays;
import java.util.List;

@Component
public class MenuCatalog {
    public record Menu(String id, String category, String name, String screenId, String description) {}
    private final List<Menu> menus;
    public MenuCatalog() {
        try (var input = getClass().getResourceAsStream("/data/menu.json")) {
            if (input == null) throw new IllegalStateException("메뉴 데이터가 없습니다.");
            menus = List.copyOf(Arrays.asList(new JsonMapper().readValue(input, Menu[].class)));
            if (menus.size() != menus.stream().map(Menu::id).distinct().count()) throw new IllegalStateException("중복 메뉴 ID");
        } catch (Exception e) { throw new IllegalStateException("메뉴 데이터를 읽지 못했습니다.", e); }
    }
    public List<Menu> all() { return menus; }
    public boolean contains(String id) { return menus.stream().anyMatch(m -> m.id().equals(id)); }
    public Menu find(String id) { return menus.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow(); }
}
