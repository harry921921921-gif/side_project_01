package fitness_tracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import fitness_tracker.entity.TrainingPlan;
import fitness_tracker.entity.User;
import fitness_tracker.service.CurrentUserService;
import fitness_tracker.service.TrainingPlanService;
import fitness_tracker.service.WorkoutPlanService;
import fitness_tracker.service.WorkoutPlanService.DayComposition;

@WebMvcTest(PlanApiController.class)
@WithMockUser(username = "test@example.com")
class PlanApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkoutPlanService workoutPlanService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private TrainingPlanService trainingPlanService;

    private User testUser() {
        User u = new User();
        u.setEmail("test@example.com");
        return u;
    }

    private TrainingPlan planWithExtraQueueCount(int n) {
        TrainingPlan p = new TrainingPlan();
        p.setExtraQueueCount(n);
        return p;
    }

    @Test
    void queueReturnsDayCompositionsForGivenDays() throws Exception {
        User user = testUser();
        given(currentUserService.getCurrentUser()).willReturn(user);
        given(workoutPlanService.currentQueueComposition(user, 3)).willReturn(List.of(
                new DayComposition("推日", List.of("臥推", "肩推"), List.of("側平舉", "三頭下壓"))
        ));
        given(trainingPlanService.applyOverrides(any(), any())).willAnswer(inv -> inv.getArgument(1));

        mockMvc.perform(get("/plan/api/queue").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayName").value("推日"))
                .andExpect(jsonPath("$[0].mainNames[0]").value("臥推"))
                .andExpect(jsonPath("$[0].accessoryPool[0]").value("側平舉"));
    }

    @Test
    void nextReturnsSingleDayComposition() throws Exception {
        User user = testUser();
        given(currentUserService.getCurrentUser()).willReturn(user);
        given(workoutPlanService.nextCompositionInCycle(user, "腿日", 3)).willReturn(
                new DayComposition("推日", List.of("臥推", "肩推"), List.of("側平舉"))
        );

        mockMvc.perform(get("/plan/api/next").param("lastDay", "腿日").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayName").value("推日"));
    }

    @Test
    void queueWithWeekParamUsesWeekAwareOverloadAndAppliesPersistedExtraQueueCount() throws Exception {
        User user = testUser();
        given(currentUserService.getCurrentUser()).willReturn(user);
        given(trainingPlanService.getOrCreateForUser(user)).willReturn(planWithExtraQueueCount(2));
        given(workoutPlanService.currentQueueComposition(user, 6, 3, 2)).willReturn(List.of(
                new DayComposition("拉 A", List.of("硬舉"), List.of("面拉", "二頭彎舉"))
        ));
        given(trainingPlanService.applyOverrides(any(), any())).willAnswer(inv -> inv.getArgument(1));

        mockMvc.perform(get("/plan/api/queue").param("days", "6").param("week", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayName").value("拉 A"))
                .andExpect(jsonPath("$[0].accessoryPool[0]").value("面拉"));
    }

    @Test
    void nextWithWeekParamUsesWeekAwareOverloadAndPersistsTheAddition() throws Exception {
        User user = testUser();
        given(currentUserService.getCurrentUser()).willReturn(user);
        // week 有帶就一律走 5 參數（含 extraOffset）多載，沒帶 extra 時預設 0
        given(workoutPlanService.nextCompositionInCycle(user, "腿 B", 6, 3, 0)).willReturn(
                new DayComposition("推 A", List.of("臥推", "肩推"), List.of("側平舉"))
        );

        mockMvc.perform(get("/plan/api/next").param("lastDay", "腿 B").param("days", "6").param("week", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayName").value("推 A"));

        verify(trainingPlanService).incrementExtraQueueCount(user);
    }

    @Test
    void nextWithExtraParamPassesItThroughToTheExtraOffsetOverload() throws Exception {
        User user = testUser();
        given(currentUserService.getCurrentUser()).willReturn(user);
        given(workoutPlanService.nextCompositionInCycle(user, "腿日", 3, 8, 3)).willReturn(
                new DayComposition("推日", List.of("臥推", "肩推"), List.of("三頭下壓"))
        );

        mockMvc.perform(get("/plan/api/next")
                        .param("lastDay", "腿日").param("days", "3").param("week", "8").param("extra", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayName").value("推日"))
                .andExpect(jsonPath("$.accessoryPool[0]").value("三頭下壓"));

        verify(trainingPlanService).incrementExtraQueueCount(user);
    }

    @Test
    void nextWithoutExtraParamDefaultsToZero() throws Exception {
        User user = testUser();
        given(currentUserService.getCurrentUser()).willReturn(user);
        given(workoutPlanService.nextCompositionInCycle(user, "腿日", 3, 8, 0)).willReturn(
                new DayComposition("推日", List.of("臥推", "肩推"), List.of("側平舉"))
        );

        mockMvc.perform(get("/plan/api/next").param("lastDay", "腿日").param("days", "3").param("week", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayName").value("推日"));
    }
}
