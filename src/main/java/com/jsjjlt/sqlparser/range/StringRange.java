package com.jsjjlt.sqlparser.range;

import lombok.Data;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Data
public class StringRange extends Range {
    private String min;
    private String max;

    public StringRange(String min, String max) {
        this.min = min;
        this.max = max;
    }

    public StringRange(String min, String max, boolean necessary) {
        this.min = min;
        this.max = max;
        this.necessary = necessary;
    }

    private static final String number = "0123456789";
    private static final String lower_character = "abcdefghijklmnopqrstuvwxyz";
    private static final String upper_character = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String special_character = "!@#$%^&*()_+-=<>?";
    private static String base_character;
    private static String byte2_character;
    private static String byte3_character;
    private static String byte4_character;
    private static String gbk_character;
    private static String latin1_character;

    private static final List<String> DATE_FORMATS = Arrays.asList(
            "yyyyMMdd",
            "yyyy-MM-dd"
    );

    public StringRange(int length) {
        base_character = number + lower_character + upper_character + special_character;
        initLatin1Character(length);
        initGBKCharacter(length);
        initByte3Character(length);
        initByte4Character(length);
    }

    /* 初始化 Latin 字符串 */
    public void initLatin1Character(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int codePoint = ThreadLocalRandom.current().nextInt(128, 256);
            sb.append(Character.toChars(codePoint));
        }
        latin1_character = sb.toString();
    }

    /* 初始化 GBK 字符串 */
    public void initGBKCharacter(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= length; i++) {
            // 50% 概率生成ASCII字符 (0-127)，50% 概率生成GBK双字节字符
            int codePoint;
            if (ThreadLocalRandom.current().nextBoolean()) {
                // ASCII部分
                codePoint = ThreadLocalRandom.current().nextInt(128);
            } else {
                // GBK双字节字符范围: 0x8140-0xFEFE (简化处理，实际需排除部分无效区域)
                int min = 0x8140;
                int max = 0xFEFE;
                codePoint = min + ThreadLocalRandom.current().nextInt(max - min + 1);
            }
            sb.append(Character.toChars(codePoint));
        }
        gbk_character = sb.toString();
    }

    /* 初始化3字节 字符串 */
    public void initByte3Character(int length) {
        int MIN_CODE_POINT = 0x0800;
        int MAX_CODE_POINT = 0xFFFF;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int codePoint = ThreadLocalRandom.current().nextInt(MAX_CODE_POINT - MIN_CODE_POINT + 1) + MIN_CODE_POINT;
            sb.append((char) codePoint);
        }
        byte3_character = sb.toString();
    }

    /* 初始化4字节 字符串 */
    public void initByte4Character(int length) {
        int[][] EMOJI_RANGES = {
                {0x1F600, 0x1F64F}, // 表情符号
                // ...（其他范围省略）
        };
    }

    public String getRandomString(long length) {
        return getRandomString(length, "");
    }

    private String getRandomString(long length, String extraCharacter) {
        String chars = base_character + (extraCharacter == null ? "" : extraCharacter);
        if (chars == null || chars.isEmpty()) {
            chars = number + lower_character + upper_character;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int idx = ThreadLocalRandom.current().nextInt(chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }

    public String getRandomString() {
        if (isDateString(min) != -1 && isDateString(max) != -1) {
            int f1 = isDateString(min);
            int f2 = isDateString(max);
            LocalDate date1 = LocalDate.parse(min, DateTimeFormatter.ofPattern(DATE_FORMATS.get(f1)));
            LocalDate date2 = LocalDate.parse(max, DateTimeFormatter.ofPattern(DATE_FORMATS.get(f2)));
            DateRange dateRange = new DateRange(min, max, DATE_FORMATS.get(f1));
            if (f1 != f2) {
                return dateRange.getRandomDate().format(DateTimeFormatter.ofPattern(DATE_FORMATS.get(f1)));
            } else {
                return dateRange.getRandomDate().toString();
            }
        } else {
            // 1. 空值/空字符串校验
            if ((min == null || min.isEmpty()) && (max == null || max.isEmpty())) {
                throw new IllegalArgumentException("strMin 和 strMax 不能同时为空");
            }
            // 处理其中一个为空的情况: 直接返回另一个（或生成同长度空字符串，根据业务调整）
            if (min == null || min.isEmpty()) {
                return max;
            }
            if (max == null || max.isEmpty()) {
                return min;
            }

            // 2. 确定生成字符串的长度（取较短的长度）
            int targetLength = Math.min(min.length(), max.length());

            // 3. 逐字符生成随机字符并拼接
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < targetLength; i++) {
                char minChar = min.charAt(i);
                char maxChar = max.charAt(i);
                // 生成当前位置的随机字符
                char randomChar = getRandomChar(minChar, maxChar);
                result.append(randomChar);
            }
            return result.toString();
        }
    }

    public String getRandomStringWithLatin1(long length) {
        return getRandomString(length, latin1_character);
    }

    public String getRandomStringWithGBK(long length) {
        return getRandomString(length, gbk_character);
    }

    public String getRandomStringWithByte3(long length) {
        String extra = gbk_character + byte3_character;
        return getRandomString(length, extra);
    }

    public String getRandomStringWithByte4(long length) {
        String extra = gbk_character + byte3_character + byte4_character;
        return getRandomString(length, extra);
    }

    public static int isDateString(String dateStr) {
        // 空字符串直接返回false
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return -1;
        }

        // 遍历所有支持的日期格式，尝试解析
        for (int i = 0; i < DATE_FORMATS.size(); i++) {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMATS.get(i));
            sdf.setLenient(false);
            try {
                sdf.parse(dateStr.trim());
                return i;
            } catch (ParseException e) {
                continue;
            }
        }
        // 所有格式都解析失败，返回false
        return -1;
    }

    private static char getRandomChar(char charMin, char charMax) {
        int minCode = (int) charMin;
        int maxCode = (int) charMax;
        // 处理 min > max 的情况，交换两者
        if (minCode > maxCode) {
            int temp = minCode;
            minCode = maxCode;
            maxCode = temp;
        }
        int randomCode = ThreadLocalRandom.current().nextInt(minCode, maxCode + 1);
        return (char) randomCode;
    }

    @Override
    public <T extends Range> void merge(T range) {
        // 实现省略
    }
}