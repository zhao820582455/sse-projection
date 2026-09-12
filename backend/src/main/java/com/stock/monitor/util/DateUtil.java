package com.stock.monitor.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期工具类
 * 交易日检测、日期格式化等
 */
public class DateUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 2025-2026年中国A股法定节假日（简化版，实际使用时需要维护）
    private static final List<LocalDate> HOLIDAYS = new ArrayList<>();

    static {
        // 2025年节假日
        HOLIDAYS.add(LocalDate.of(2025, 1, 1));   // 元旦
        HOLIDAYS.add(LocalDate.of(2025, 1, 28));  // 春节假期开始
        HOLIDAYS.add(LocalDate.of(2025, 1, 29));
        HOLIDAYS.add(LocalDate.of(2025, 1, 30));
        HOLIDAYS.add(LocalDate.of(2025, 1, 31));
        HOLIDAYS.add(LocalDate.of(2025, 2, 3));
        HOLIDAYS.add(LocalDate.of(2025, 2, 4));
        HOLIDAYS.add(LocalDate.of(2025, 4, 4));   // 清明节
        HOLIDAYS.add(LocalDate.of(2025, 5, 1));   // 劳动节
        HOLIDAYS.add(LocalDate.of(2025, 5, 2));
        HOLIDAYS.add(LocalDate.of(2025, 5, 5));
        HOLIDAYS.add(LocalDate.of(2025, 6, 2));   // 端午节
        HOLIDAYS.add(LocalDate.of(2025, 10, 1));  // 国庆节
        HOLIDAYS.add(LocalDate.of(2025, 10, 2));
        HOLIDAYS.add(LocalDate.of(2025, 10, 3));
        HOLIDAYS.add(LocalDate.of(2025, 10, 6));
        HOLIDAYS.add(LocalDate.of(2025, 10, 7));

        // 2026年节假日
        HOLIDAYS.add(LocalDate.of(2026, 1, 1));   // 元旦
        HOLIDAYS.add(LocalDate.of(2026, 2, 16));  // 春节
        HOLIDAYS.add(LocalDate.of(2026, 2, 17));
        HOLIDAYS.add(LocalDate.of(2026, 2, 18));
        HOLIDAYS.add(LocalDate.of(2026, 2, 19));
        HOLIDAYS.add(LocalDate.of(2026, 2, 20));
        HOLIDAYS.add(LocalDate.of(2026, 4, 6));   // 清明节
        HOLIDAYS.add(LocalDate.of(2026, 5, 1));   // 劳动节
        HOLIDAYS.add(LocalDate.of(2026, 6, 1));   // 端午节
        HOLIDAYS.add(LocalDate.of(2026, 10, 1));  // 国庆节
    }

    /**
     * 判断是否为交易日（周一到周五且非法定节假日）
     */
    public static boolean isTradingDay(LocalDate date) {
        if (date == null) return false;
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        return !HOLIDAYS.contains(date);
    }

    /**
     * 获取当前交易日（含当天）
     */
    public static LocalDate getCurrentTradingDay() {
        return getLatestTradingDay();
    }

    /**
     * 获取最近的交易日（含当天）
     */
    public static LocalDate getLatestTradingDay() {
        LocalDate today = LocalDate.now();
        while (!isTradingDay(today)) {
            today = today.minusDays(1);
        }
        return today;
    }

    /**
     * 获取前N个交易日
     */
    public static LocalDate getPreviousTradingDay(LocalDate from, int daysBefore) {
        LocalDate result = from;
        int count = 0;
        while (count < daysBefore) {
            result = result.minusDays(1);
            if (isTradingDay(result)) {
                count++;
            }
        }
        return result;
    }

    /**
     * 获取上一个交易日
     */
    public static LocalDate getPreviousTradingDay(LocalDate from) {
        return getPreviousTradingDay(from, 1);
    }

    /**
     * 格式化日期为字符串
     */
    public static String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_FORMATTER);
    }

    /**
     * 格式化日期时间为字符串
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DATETIME_FORMATTER);
    }

    /**
     * 解析日期字符串
     */
    public static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断当前时间是否在交易时段内（9:30-15:00）
     */
    public static boolean isInTradingHours() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        if (hour < 9 || hour > 15) return false;
        if (hour == 9 && minute < 30) return false;
        if (hour == 15 && minute > 0) return false;
        return true;
    }

    /**
     * 判断当前时间是否为收盘校准时间（15:10之后）
     */
    public static boolean isAfterMarketClose() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        return hour > 15 || (hour == 15 && minute >= 10);
    }

    /**
     * 判断给定日期是否为今天
     */
    public static boolean isToday(LocalDate date) {
        return date != null && date.equals(LocalDate.now());
    }
}
