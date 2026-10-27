package com.wontake.assist;

import com.wontake.ai.AiClient.BlockInfo;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** R5 업무 블록 사전(blocks.json). AI는 이 안에서만 고른다. */
@Component
public class BlockCatalog {
    public record Block(String id, String name, String menuId, String type, String target, String param,
                        List<String> requires, String skipWhen, String description) {
        public boolean cardTarget() { return "CARD".equals(target); }
        public boolean currencyParam() { return "CURRENCY".equals(param); }
    }
    private final List<Block> blocks;
    public BlockCatalog() {
        try (var input = getClass().getResourceAsStream("/data/blocks.json")) {
            if (input == null) throw new IllegalStateException("블록 데이터가 없습니다.");
            blocks = List.copyOf(Arrays.asList(new JsonMapper().readValue(input, Block[].class)));
        } catch (Exception e) { throw new IllegalStateException("블록 데이터를 읽지 못했습니다.", e); }
        var ids = blocks.stream().map(Block::id).toList();
        if (ids.size() != new HashSet<>(ids).size()) throw new IllegalStateException("중복 블록 ID");
        for (var b : blocks) if (!ids.containsAll(b.requires())) throw new IllegalStateException("선행 블록 확인: " + b.id());
    }
    public List<Block> all() { return blocks; }
    public List<String> ids() { return blocks.stream().map(Block::id).toList(); }
    public Block find(String id) { return blocks.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null); }
    /** AI 입력용 사전. skipWhen은 서버 검증용이라 보내지 않는다. */
    public List<BlockInfo> forAi() {
        return blocks.stream().map(b -> new BlockInfo(b.id(), b.name(), b.type(), b.target(), b.param(), b.requires(), b.description())).toList();
    }
}
