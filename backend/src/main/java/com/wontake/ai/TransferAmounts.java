package com.wontake.ai;

import com.wontake.api.ApiException;
import com.wontake.assist.Redactor;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** R5의 금액 비공개 원칙과 이체 금액 연결을 함께 지킨다. AI는 참조만 고르고 서버가 원문 금액을 복원한다. */
public final class TransferAmounts {
    private TransferAmounts() {}
    public record Masked(String text, Map<String, Long> values) {}
    private static final Pattern REF = Pattern.compile("amount-\\d+");

    public static Masked mask(String input) {
        if (REF.matcher(input).find()) throw ApiException.invalid("금액은 숫자와 원 단위로 말씀해 주세요.");
        var matcher = Redactor.AMOUNT.matcher(input);
        var text = new StringBuffer();
        var values = new LinkedHashMap<String, Long>();
        while (matcher.find()) {
            String value = matcher.group().replaceAll("[\\s,원]", "");
            long amount;
            try { amount = parse(value); }
            catch (ArithmeticException | NumberFormatException e) {
                throw new ApiException(502, "AI_INVALID_RESPONSE", "금액을 정확한 원 단위 정수로 확인하지 못했어요.");
            }
            String ref = "amount-" + (values.size() + 1);
            values.put(ref, amount);
            matcher.appendReplacement(text, Matcher.quoteReplacement(ref));
        }
        matcher.appendTail(text);
        return new Masked(text.toString(), Collections.unmodifiableMap(values));
    }

    /** 큰 단위(만·억)에서 앞의 천 단위를 묶는다. 소수는 원 단위에서 정확히 정수가 될 때만 허용한다. */
    private static long parse(String value) {
        boolean negative = value.startsWith("-");
        value = value.replaceFirst("^[-+]", "");
        var terms = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(천)?|([만억])").matcher(value);
        BigDecimal total = BigDecimal.ZERO, section = BigDecimal.ZERO;
        int end = 0;
        while (terms.find()) {
            if (terms.start() != end) throw new NumberFormatException();
            end = terms.end();
            if (terms.group(1) != null) section = section.add(new BigDecimal(terms.group(1)).multiply(BigDecimal.valueOf(terms.group(2) == null ? 1 : 1000)));
            else {
                section = section.multiply(BigDecimal.valueOf(terms.group(3).equals("억") ? 100_000_000L : 10_000L));
                total = total.add(section);
                section = BigDecimal.ZERO;
            }
        }
        if (end != value.length()) throw new NumberFormatException();
        var amount = total.add(section);
        return (negative ? amount.negate() : amount).longValueExact();
    }

    /** 모의 구현도 동일한 참조에서 읽는다. 실제 금액을 AI가 새로 만들 수 없다. */
    public static Long find(String text, Map<String, Long> values) {
        var matcher = REF.matcher(text);
        return matcher.find() ? resolve(matcher.group(), values) : null;
    }
    public static Long resolve(String ref, Map<String, Long> values) {
        if (ref == null) return null;
        if (!values.containsKey(ref)) throw new ApiException(502, "AI_INVALID_RESPONSE", "AI가 선택한 금액을 확인하지 못했어요.");
        return values.get(ref);
    }
}
