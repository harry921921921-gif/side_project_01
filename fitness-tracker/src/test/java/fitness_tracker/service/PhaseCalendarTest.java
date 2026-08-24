package fitness_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import fitness_tracker.service.PhaseCalendar.PhaseType;

class PhaseCalendarTest {

    @Test
    void cycleWeekReturnsSameValueWithinFirstTwentyWeeks() {
        assertEquals(1, PhaseCalendar.cycleWeek(1));
        assertEquals(13, PhaseCalendar.cycleWeek(13));
        assertEquals(20, PhaseCalendar.cycleWeek(20));
    }

    // 使用者持續使用超過 20 週（一輪跑完）要折回第 1 週重新開始一輪，不是永遠停在最大力量期
    @Test
    void cycleWeekWrapsBackToOneAfterTwentyWeeks() {
        assertEquals(1, PhaseCalendar.cycleWeek(21));
        assertEquals(13, PhaseCalendar.cycleWeek(33)); // 33 = 20 + 13
        assertEquals(20, PhaseCalendar.cycleWeek(40));
        assertEquals(1, PhaseCalendar.cycleWeek(41));
    }

    @Test
    void cycleWeekWrapsAcrossMultipleFullCycles() {
        assertEquals(1, PhaseCalendar.cycleWeek(101)); // 5 個完整輪 (100) 之後回到第 1 週
        assertEquals(7, PhaseCalendar.cycleWeek(107));
    }

    @Test
    void phaseForWeekAfterTwentyWeeksCyclesBackToAdaptInsteadOfStayingAtStrength() {
        assertEquals(PhaseType.ADAPT, PhaseCalendar.phaseForWeek(21));
        assertEquals(PhaseType.HYPER, PhaseCalendar.phaseForWeek(28)); // 28 = 20 + 8 -> 第 8 週 -> 肌肥大
        assertEquals(PhaseType.STRENGTH, PhaseCalendar.phaseForWeek(35)); // 35 = 20 + 15 -> 第 15 週 -> 最大力量
    }

    @Test
    void phaseForWeekWithinFirstCycleUnchanged() {
        assertEquals(PhaseType.ADAPT, PhaseCalendar.phaseForWeek(6));
        assertEquals(PhaseType.HYPER, PhaseCalendar.phaseForWeek(7));
        assertEquals(PhaseType.HYPER, PhaseCalendar.phaseForWeek(14));
        assertEquals(PhaseType.STRENGTH, PhaseCalendar.phaseForWeek(15));
        assertEquals(PhaseType.STRENGTH, PhaseCalendar.phaseForWeek(20));
    }
}
